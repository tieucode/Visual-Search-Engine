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
import com.visualsearch.indexing.persistence.ImageState;
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
    private final IndexingMessageValidator validator;
    private final ImagePreparation preparation;
    private final AiResultMapper resultMapper;
    private final RetryPolicy retryPolicy;
    private final IndexingStateService stateService;
    private final AiServiceClient aiServiceClient;
    private final QdrantIndexClient qdrantIndexClient;

    public ProcessingResult process(ImageIndexingMessage message, int currentAttempt) {
        long started = System.nanoTime();
        List<ImageIndexingMessage.ImageItem> uniqueItems = validator.validate(message, currentAttempt);
        Map<UUID, ImageState> states = stateService.findByImageIds(
                uniqueItems.stream().map(ImageIndexingMessage.ImageItem::imageId).toList());

        List<UUID> missing = uniqueItems.stream()
                .map(ImageIndexingMessage.ImageItem::imageId)
                .filter(imageId -> !states.containsKey(imageId))
                .toList();
        if (!missing.isEmpty()) {
            throw new InvalidIndexingMessageException("No image_index row exists for imageIds " + missing);
        }

        List<ImageIndexingMessage.ImageItem> pendingItems = uniqueItems.stream()
                .filter(item -> states.get(item.imageId()).status() == IndexStatus.PENDING)
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
            ImageState state = states.get(item.imageId());
            try {
                PreparedImage image = preparation.prepare(item);
                prepared.add(new PreparedWork(item, state, image));
            } catch (PermanentImageException exception) {
                if (stateService.recordPermanentImageFailure(
                        item.imageId(), state.batchId(), exception.getMessage())) {
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

        int aiBatchSize = properties.getAi().getBatchSize();
        for (int start = 0; start < prepared.size(); start += aiBatchSize) {
            List<PreparedWork> aiBatch = prepared.subList(
                    start, Math.min(start + aiBatchSize, prepared.size()));
            try {
                AiBatchResponse aiResponse = aiServiceClient.index(
                        aiBatch.stream().map(PreparedWork::image).toList(),
                        () -> markBatchRetryStarted(aiBatch, currentAttempt));
                applyAiResults(aiBatch, aiResponse, currentAttempt, retries, counters);
            } catch (AiCircuitOpenException exception) {
                addRetry(retries, currentAttempt, aiBatch.stream().map(PreparedWork::item).toList());
            } catch (AiServiceException exception) {
                handleAiRequestFailure(aiBatch, exception, currentAttempt, retries, counters);
            }
        }
        return result(retries, counters, started);
    }

    private void handleAiRequestFailure(
            List<PreparedWork> aiBatch,
            AiServiceException exception,
            int currentAttempt,
            Map<Integer, List<ImageIndexingMessage.ImageItem>> retries,
            Counters counters) {
        for (PreparedWork work : aiBatch) {
            if (exception.isRetryable()) {
                handleTransient(
                        work.item(), work.state(), currentAttempt, exception.getMessage(),
                        currentAttempt > 0, retries, counters);
            } else if (stateService.recordPermanentFailure(
                    work.item().imageId(), work.state().batchId(), currentAttempt, exception.getMessage())) {
                counters.failed++;
            } else {
                counters.skipped++;
            }
        }
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
        Map<UUID, List<AiBatchResponse.Result>> grouped = resultMapper.group(response,
                prepared.stream().map(work -> work.item().imageId()).collect(Collectors.toSet()));

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
                        work.state().batchId(),
                        result.safeEmbedding());
                List<OcrData> ocrItems = result.safeOcr().stream().map(resultMapper::toOcrData).toList();
                if (stateService.recordSuccess(work.item().imageId(), work.state().batchId(), ocrItems)) {
                    counters.succeeded++;
                    log.info("Image indexing succeeded imageId={} batchId={} retryCount={}",
                            work.item().imageId(), work.state().batchId(), currentAttempt);
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
                        work.item().imageId(), work.state().batchId(), currentAttempt, exception.getMessage())) {
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
                work.item().imageId(), work.state().batchId(), currentAttempt, message)) {
            counters.failed++;
        } else {
            counters.skipped++;
        }
    }

    private void handleTransient(
            ImageIndexingMessage.ImageItem item,
            ImageState state,
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

        if (retryPolicy.exhausted(currentAttempt)) {
            if (stateService.recordRetriesExhausted(
                    item.imageId(), state.batchId(), currentAttempt, errorMessage)) {
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
            ImageState state,
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
