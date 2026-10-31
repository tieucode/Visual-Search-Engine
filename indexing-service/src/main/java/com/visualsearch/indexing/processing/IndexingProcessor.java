package com.visualsearch.indexing.processing;

import com.visualsearch.indexing.client.ai.AiBatchResponse;
import com.visualsearch.indexing.client.ai.AiServiceClient;
import com.visualsearch.indexing.client.qdrant.QdrantIndexClient;
import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.exception.AiCircuitOpenException;
import com.visualsearch.indexing.exception.AiServiceException;
import com.visualsearch.indexing.exception.InvalidIndexingMessageException;
import com.visualsearch.indexing.exception.PermanentImageException;
import com.visualsearch.indexing.exception.TransientDependencyException;
import com.visualsearch.indexing.image.PreparedImage;
import com.visualsearch.indexing.messaging.ImageIndexingMessage;
import com.visualsearch.indexing.messaging.ImageIndexingMessage.ImageItem;
import com.visualsearch.indexing.persistence.ImageState;
import com.visualsearch.indexing.persistence.IndexStatus;
import com.visualsearch.indexing.persistence.IndexWrite;
import com.visualsearch.indexing.persistence.IndexingStateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingProcessor {
    private final IndexingProperties properties;
    private final IndexingMessageValidator validator;
    private final ImagePreparation preparation;
    private final ThreadPoolTaskExecutor imagePreparationExecutor;
    private final AiResultMapper resultMapper;
    private final RetryPolicy retryPolicy;
    private final IndexingStateService stateService;
    private final AiServiceClient aiServiceClient;
    private final QdrantIndexClient qdrantIndexClient;

    public ProcessingResult process(ImageIndexingMessage message, int attempt) {
        long started = System.nanoTime();
        List<ImageItem> items = validator.validate(message, attempt);
        Map<UUID, ImageState> states =
                stateService.findByImageIds(items.stream().map(ImageItem::imageId).toList());
        List<UUID> missing =
                items.stream()
                        .map(ImageItem::imageId)
                        .filter(id -> !states.containsKey(id))
                        .toList();
        if (!missing.isEmpty())
            throw new InvalidIndexingMessageException(
                    "No image_index row exists for imageIds " + missing);
        List<ImageItem> pending =
                items.stream()
                        .filter(item -> states.get(item.imageId()).status() == IndexStatus.PENDING)
                        .toList();
        Progress progress = new Progress(items.size() - pending.size());
        if (!pending.isEmpty()) {
            if (!aiServiceClient.isAvailableForBusiness()) {
                progress.defer(pending, attempt);
            } else {
                try (var pipeline =
                        new PreparationPipeline(
                                pending,
                                properties.getAi().getBatchSize(),
                                imagePreparationExecutor,
                                preparation)) {
                    while (pipeline.hasNext()) {
                        requireRunning();
                        if (!aiServiceClient.isAvailableForBusiness()) {
                            progress.defer(pipeline.remaining(), attempt);
                            break;
                        }
                        var outcomes = new OutcomeGroup(attempt);
                        List<PreparedWork> prepared = new ArrayList<>();
                        for (var outcome : pipeline.next()) {
                            if (outcome.failure() == null) {
                                prepared.add(
                                        new PreparedWork(
                                                outcome.item(),
                                                states.get(outcome.item().imageId()),
                                                outcome.image()));
                            } else if (outcome.failure()
                                    instanceof PermanentImageException failure) {
                                outcomes.fail(outcome.item(), failure.getMessage(), -1);
                            } else if (outcome.failure()
                                    instanceof TransientDependencyException failure) {
                                outcomes.transientFailure(outcome.item(), failure.getMessage());
                            } else throw outcome.failure();
                        }
                        requireRunning();
                        processPrepared(prepared, outcomes, progress);
                        requireRunning();
                        applyOutcomes(outcomes, progress);
                    }
                }
            }
        }
        log.info(
                "Indexing completed success={} failed={} skipped={} retryGroups={} durationMs={}",
                progress.succeeded,
                progress.failed,
                progress.skipped,
                progress.retries.keySet(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return new ProcessingResult(
                progress.retries, progress.succeeded, progress.failed, progress.skipped);
    }

    private void processPrepared(
            List<PreparedWork> prepared, OutcomeGroup outcomes, Progress progress) {
        if (prepared.isEmpty()) return;
        AiBatchResponse response;
        try {
            response =
                    aiServiceClient.index(
                            prepared.stream().map(PreparedWork::image).toList(),
                            () ->
                                    stateService.markRetryStarted(
                                            prepared.stream()
                                                    .map(work -> work.item().imageId())
                                                    .toList(),
                                            outcomes.attempt));
        } catch (AiCircuitOpenException exception) {
            progress.defer(prepared.stream().map(PreparedWork::item).toList(), outcomes.attempt);
            return;
        } catch (AiServiceException exception) {
            prepared.forEach(
                    work ->
                            outcomes.failure(
                                    work.item(), exception.getMessage(), exception.isRetryable()));
            return;
        }
        var grouped =
                resultMapper.group(
                        response,
                        prepared.stream()
                                .map(work -> work.item().imageId())
                                .collect(Collectors.toSet()));
        Map<UUID, AiBatchResponse.Result> successes = new LinkedHashMap<>();
        for (PreparedWork work : prepared) {
            var matches = grouped.getOrDefault(work.item().imageId(), List.of());
            if (matches.size() != 1) {
                outcomes.transientFailure(
                        work.item(),
                        "AI response is missing or duplicates imageId " + work.item().imageId());
            } else {
                var result = matches.getFirst();
                if (result.successful()) successes.put(work.item().imageId(), result);
                else {
                    var error = result.error();
                    outcomes.failure(
                            work.item(),
                            error == null
                                    ? "AI failed without an error description"
                                    : error.code() + ": " + error.message(),
                            error != null && error.isRetryable());
                }
            }
        }
        var successfulWork =
                prepared.stream()
                        .filter(work -> successes.containsKey(work.item().imageId()))
                        .toList();
        var failures =
                qdrantIndexClient.upsertBatch(
                        successfulWork.stream()
                                .map(
                                        work ->
                                                new QdrantIndexClient.Point(
                                                        work.item().imageId(),
                                                        work.state().batchId(),
                                                        successes
                                                                .get(work.item().imageId())
                                                                .safeEmbedding()))
                                .toList());
        for (PreparedWork work : successfulWork) {
            var failure = failures.get(work.item().imageId());
            if (failure == null) {
                outcomes.writes.put(
                        work.item().imageId(),
                        new IndexWrite(
                                work.item().imageId(),
                                IndexStatus.SUCCESS,
                                outcomes.attempt,
                                null,
                                successes.get(work.item().imageId()).safeOcr().stream()
                                        .map(resultMapper::toOcrData)
                                        .toList()));
            } else outcomes.failure(work.item(), failure.getMessage(), failure.isRetryable());
        }
    }

    private void applyOutcomes(OutcomeGroup outcomes, Progress progress) {
        var changed = stateService.recordOutcomes(outcomes.writes.values());
        for (var write : outcomes.writes.values()) {
            IndexStatus status = changed.get(write.imageId());
            if (status == null) progress.skipped++;
            else if (status == IndexStatus.SUCCESS) progress.succeeded++;
            else if (status == IndexStatus.FAILED) progress.failed++;
            else
                progress.defer(
                        List.of(outcomes.retryItems.get(write.imageId())), outcomes.attempt + 1);
        }
    }

    private void requireRunning() {
        if (Thread.currentThread().isInterrupted()
                || imagePreparationExecutor.getThreadPoolExecutor().isShutdown()) {
            throw new IllegalStateException("Indexing is shutting down");
        }
    }

    private record PreparedWork(ImageItem item, ImageState state, PreparedImage image) {}

    private final class OutcomeGroup {
        private final int attempt;
        private final Map<UUID, IndexWrite> writes = new LinkedHashMap<>();
        private final Map<UUID, ImageItem> retryItems = new HashMap<>();

        private OutcomeGroup(int attempt) {
            this.attempt = attempt;
        }

        private void fail(ImageItem item, String error, int recordedAttempt) {
            writes.put(
                    item.imageId(),
                    new IndexWrite(
                            item.imageId(), IndexStatus.FAILED, recordedAttempt, error, List.of()));
        }

        private void failure(ImageItem item, String error, boolean retryable) {
            if (retryable) transientFailure(item, error);
            else fail(item, error, attempt);
        }

        private void transientFailure(ImageItem item, String error) {
            if (retryPolicy.exhausted(attempt)) fail(item, error, attempt);
            else {
                writes.put(
                        item.imageId(),
                        new IndexWrite(
                                item.imageId(), IndexStatus.PENDING, attempt, error, List.of()));
                retryItems.put(item.imageId(), item);
                log.warn(
                        "Transient indexing failure imageId={} attempt={} reason={}",
                        item.imageId(),
                        attempt,
                        error);
            }
        }
    }

    private static final class Progress {
        private final Map<Integer, List<ImageItem>> retries = new LinkedHashMap<>();
        private int succeeded;
        private int failed;
        private int skipped;

        private Progress(int skipped) {
            this.skipped = skipped;
        }

        private void defer(List<ImageItem> items, int attempt) {
            retries.computeIfAbsent(attempt, ignored -> new ArrayList<>()).addAll(items);
        }
    }
}
