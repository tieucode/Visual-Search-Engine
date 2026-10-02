package com.visualsearch.indexing.processing;

import com.visualsearch.indexing.messaging.ImageIndexingMessage;
import com.visualsearch.indexing.client.ai.AiBatchResponse;
import com.visualsearch.indexing.client.ai.AiServiceClient;
import com.visualsearch.indexing.client.qdrant.QdrantIndexClient;
import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.exception.AiCircuitOpenException;
import com.visualsearch.indexing.exception.AiServiceException;
import com.visualsearch.indexing.exception.InvalidIndexingMessageException;
import com.visualsearch.indexing.exception.PermanentImageException;
import com.visualsearch.indexing.exception.QdrantOperationException;
import com.visualsearch.indexing.exception.TransientDependencyException;
import com.visualsearch.indexing.image.BoundedHttpImageSource;
import com.visualsearch.indexing.image.DownloadedImage;
import com.visualsearch.indexing.image.ImagePreprocessor;
import com.visualsearch.indexing.image.PreparedImage;
import com.visualsearch.indexing.persistence.ImageIndexEntity;
import com.visualsearch.indexing.persistence.IndexStatus;
import com.visualsearch.indexing.persistence.IndexingStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingProcessor {

    private final IndexingProperties properties;
    private final IndexingStateService stateService;
    private final BoundedHttpImageSource imageSource;
    private final ImagePreprocessor imagePreprocessor;
    private final AiServiceClient aiServiceClient;
    private final QdrantIndexClient qdrantIndexClient;

    public ProcessingResult process(ImageIndexingMessage message, int currentAttempt) {
        long started = System.nanoTime();
        List<ImageIndexingMessage.ImageItem> uniqueItems = validate(message, currentAttempt);
        Map<UUID, ImageIndexEntity> states = stateService.findByImageIds(
                uniqueItems.stream().map(ImageIndexingMessage.ImageItem::imageId).toList());

        List<UUID> missing = uniqueItems.stream()
                .map(ImageIndexingMessage.ImageItem::imageId)
                .filter(imageId -> !states.containsKey(imageId))
                .toList();
        if (!missing.isEmpty()) {
            throw new InvalidIndexingMessageException("No image_index row exists for imageIds " + missing);
        }

        List<ImageIndexingMessage.ImageItem> pendingItems = uniqueItems.stream()
                .filter(item -> states.get(item.imageId()).getStatus() == IndexStatus.PENDING)
                .toList();
        int skipped = uniqueItems.size() - pendingItems.size();
        if (pendingItems.isEmpty()) {
            return new ProcessingResult(Map.of(), 0, 0, skipped);
        }

        Map<Integer, List<ImageIndexingMessage.ImageItem>> retries = new LinkedHashMap<>();
        Counters counters = new Counters(skipped);

        if (!aiServiceClient.isAvailableForBusiness()) {
            addRetry(retries, currentAttempt, pendingItems);
            log.info("Indexing deferred because AI circuit is unavailable attempt={} imageCount={}",
                    currentAttempt, pendingItems.size());
            return result(retries, counters, started);
        }

        List<PreparedWork> prepared = new ArrayList<>();
        for (ImageIndexingMessage.ImageItem item : pendingItems) {
            ImageIndexEntity state = states.get(item.imageId());
            try {
                DownloadedImage downloaded = imageSource.download(item.imageUrl());
                PreparedImage image = imagePreprocessor.preprocess(item.imageId(), downloaded);
                prepared.add(new PreparedWork(item, state, image));
            } catch (PermanentImageException exception) {
                if (stateService.recordPermanentImageFailure(
                        item.imageId(), state.getBatchId(), exception.getMessage())) {
                    counters.failed++;
                } else {
                    counters.skipped++;
                }
                log.warn("Permanent image failure imageId={} reason={}",
                        item.imageId(), exception.getMessage());
            } catch (TransientDependencyException exception) {
                handleTransient(item, state, currentAttempt, exception.getMessage(), false, retries, counters);
            }
        }

        if (prepared.isEmpty()) {
            return result(retries, counters, started);
        }

        AiBatchResponse aiResponse;
        try {
            aiResponse = aiServiceClient.index(
                    prepared.stream().map(PreparedWork::image).toList(),
                    () -> markBatchRetryStarted(prepared, currentAttempt));
        } catch (AiCircuitOpenException exception) {
            addRetry(retries, currentAttempt, prepared.stream().map(PreparedWork::item).toList());
            return result(retries, counters, started);
        } catch (AiServiceException exception) {
            for (PreparedWork work : prepared) {
                if (exception.isRetryable()) {
                    handleTransient(
                            work.item(),
                            work.state(),
                            currentAttempt,
                            exception.getMessage(),
                            currentAttempt > 0,
                            retries,
                            counters);
                } else if (stateService.recordPermanentFailure(
                        work.item().imageId(), work.state().getBatchId(), currentAttempt, exception.getMessage())) {
                    counters.failed++;
                } else {
                    counters.skipped++;
                }
            }
            return result(retries, counters, started);
        }

        applyAiResults(prepared, aiResponse, currentAttempt, retries, counters);
        return result(retries, counters, started);
    }

    private List<ImageIndexingMessage.ImageItem> validate(ImageIndexingMessage message, int currentAttempt) {
        if (message == null || message.images() == null || message.images().isEmpty()) {
            throw new InvalidIndexingMessageException("Indexing message must contain at least one image");
        }
        if (message.images().size() > properties.getImage().getMaxBatchImages()) {
            throw new InvalidIndexingMessageException(
                    "Indexing message exceeds max batch size " + properties.getImage().getMaxBatchImages());
        }
        if (currentAttempt < 0 || currentAttempt > properties.getMaxRetries()) {
            throw new InvalidIndexingMessageException("Invalid retry attempt " + currentAttempt);
        }

        Map<UUID, ImageIndexingMessage.ImageItem> unique = new LinkedHashMap<>();
        for (ImageIndexingMessage.ImageItem item : message.images()) {
            if (item == null || item.imageId() == null || item.imageUrl() == null || item.imageUrl().isBlank()) {
                throw new InvalidIndexingMessageException("Every message item requires imageId and imageUrl");
            }
            ImageIndexingMessage.ImageItem previous = unique.putIfAbsent(item.imageId(), item);
            if (previous != null && !previous.imageUrl().equals(item.imageUrl())) {
                throw new InvalidIndexingMessageException(
                        "Duplicate imageId has conflicting imageUrl: " + item.imageId());
            }
        }
        return List.copyOf(unique.values());
    }

    private void markBatchRetryStarted(List<PreparedWork> prepared, int currentAttempt) {
        if (currentAttempt <= 0) {
            return;
        }
        for (PreparedWork work : prepared) {
            stateService.markRetryStarted(work.item().imageId(), currentAttempt);
        }
    }

    private void applyAiResults(
            List<PreparedWork> prepared,
            AiBatchResponse response,
            int currentAttempt,
            Map<Integer, List<ImageIndexingMessage.ImageItem>> retries,
            Counters counters) {
        Map<UUID, List<AiBatchResponse.Result>> grouped = response.results().stream()
                .filter(result -> result.imageId() != null)
                .collect(Collectors.groupingBy(AiBatchResponse.Result::imageId));
        Set<UUID> requestedIds = prepared.stream()
                .map(work -> work.item().imageId())
                .collect(Collectors.toSet());
        Set<UUID> unknownIds = new HashSet<>(grouped.keySet());
        unknownIds.removeAll(requestedIds);
        if (!unknownIds.isEmpty()) {
            log.warn("AI response contained unknown imageIds={}", unknownIds);
        }

        for (PreparedWork work : prepared) {
            List<AiBatchResponse.Result> matches = grouped.getOrDefault(work.item().imageId(), List.of());
            if (matches.size() != 1) {
                handleTransient(
                        work.item(),
                        work.state(),
                        currentAttempt,
                        matches.isEmpty()
                                ? "AI response is missing imageId " + work.item().imageId()
                                : "AI response contains duplicate imageId " + work.item().imageId(),
                        currentAttempt > 0,
                        retries,
                        counters);
                continue;
            }

            AiBatchResponse.Result result = matches.getFirst();
            if (!result.successful()) {
                handleAiItemFailure(work, result, currentAttempt, retries, counters);
                continue;
            }

            try {
                qdrantIndexClient.upsert(
                        work.item().imageId(),
                        work.state().getBatchId(),
                        result.safeEmbedding());
                List<OcrData> ocrItems = result.safeOcr().stream().map(this::toOcrData).toList();
                if (stateService.recordSuccess(work.item().imageId(), work.state().getBatchId(), ocrItems)) {
                    counters.succeeded++;
                    log.info("Image indexing succeeded imageId={} batchId={} retryCount={}",
                            work.item().imageId(), work.state().getBatchId(), currentAttempt);
                } else {
                    counters.skipped++;
                }
            } catch (QdrantOperationException exception) {
                if (exception.isRetryable()) {
                    handleTransient(
                            work.item(),
                            work.state(),
                            currentAttempt,
                            exception.getMessage(),
                            currentAttempt > 0,
                            retries,
                            counters);
                } else if (stateService.recordPermanentFailure(
                        work.item().imageId(), work.state().getBatchId(), currentAttempt, exception.getMessage())) {
                    counters.failed++;
                } else {
                    counters.skipped++;
                }
            }
        }
    }

    private void handleAiItemFailure(
            PreparedWork work,
            AiBatchResponse.Result result,
            int currentAttempt,
            Map<Integer, List<ImageIndexingMessage.ImageItem>> retries,
            Counters counters) {
        AiBatchResponse.ErrorItem error = result.error();
        String message = error == null
                ? "AI failed image without an error description"
                : (error.code() == null ? "AI_ERROR" : error.code()) + ": " + error.message();
        if (error != null && error.isRetryable()) {
            handleTransient(
                    work.item(), work.state(), currentAttempt, message, currentAttempt > 0, retries, counters);
        } else if (stateService.recordPermanentFailure(
                work.item().imageId(), work.state().getBatchId(), currentAttempt, message)) {
            counters.failed++;
        } else {
            counters.skipped++;
        }
    }

    private OcrData toOcrData(AiBatchResponse.OcrItem item) {
        AiBatchResponse.BoundingBox box = item.boundingBox();
        OcrData.BoundingBox boundingBox = box == null ? null : new OcrData.BoundingBox(
                safeFloat(box.x()), safeFloat(box.y()), safeFloat(box.width()), safeFloat(box.height()));
        return new OcrData(
                item.text(),
                item.normalizedText(),
                safeFloat(item.confidence()),
                boundingBox);
    }

    private float safeFloat(Float value) {
        return value == null ? 0.0f : value;
    }

    private void handleTransient(
            ImageIndexingMessage.ImageItem item,
            ImageIndexEntity state,
            int currentAttempt,
            String errorMessage,
            boolean attemptAlreadyRecorded,
            Map<Integer, List<ImageIndexingMessage.ImageItem>> retries,
            Counters counters) {
        if (currentAttempt > 0 && !attemptAlreadyRecorded) {
            if (!stateService.markRetryStarted(item.imageId(), currentAttempt)) {
                counters.skipped++;
                return;
            }
        }

        if (currentAttempt >= properties.getMaxRetries()) {
            if (stateService.recordRetriesExhausted(
                    item.imageId(), state.getBatchId(), currentAttempt, errorMessage)) {
                counters.failed++;
            } else {
                counters.skipped++;
            }
            return;
        }

        if (stateService.recordTransientFailure(item.imageId(), errorMessage)) {
            addRetry(retries, currentAttempt + 1, List.of(item));
            log.warn("Transient indexing failure imageId={} attempt={} nextAttempt={} reason={}",
                    item.imageId(), currentAttempt, currentAttempt + 1, errorMessage);
        } else {
            counters.skipped++;
        }
    }

    private void addRetry(
            Map<Integer, List<ImageIndexingMessage.ImageItem>> retries,
            int attempt,
            List<ImageIndexingMessage.ImageItem> items) {
        retries.computeIfAbsent(attempt, ignored -> new ArrayList<>()).addAll(items);
    }

    private ProcessingResult result(
            Map<Integer, List<ImageIndexingMessage.ImageItem>> retries,
            Counters counters,
            long startedNanos) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        log.info("Indexing processing completed success={} failed={} skipped={} retryGroups={} durationMs={}",
                counters.succeeded,
                counters.failed,
                counters.skipped,
                retries.keySet(),
                durationMs);
        return new ProcessingResult(retries, counters.succeeded, counters.failed, counters.skipped);
    }

    private record PreparedWork(
            ImageIndexingMessage.ImageItem item,
            ImageIndexEntity state,
            PreparedImage image) {
    }

    private static final class Counters {
        private int succeeded;
        private int failed;
        private int skipped;

        private Counters(int skipped) {
            this.skipped = skipped;
        }
    }
}
