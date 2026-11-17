package com.visualsearch.indexing.messaging;

import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.persistence.IndexingJobOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexingJobOutboxPoller {
    private final IndexingJobOutboxRepository repository;
    private final IndexingMessagePublisher publisher;
    private final IndexingProperties properties;

    @Scheduled(fixedDelayString = "${indexing.job-outbox.poll-interval:5000}")
    public void poll() {
        var settings = properties.getJobOutbox();
        repository.releaseStaleClaims(settings.getClaimTimeout());
        var jobs = repository.claimReady(properties.getImage().getMaxBatchImages());
        if (jobs.isEmpty()) return;

        try {
            publisher.publishInitial(jobs.stream()
                    .map(job -> new ImageIndexingMessage.ImageItem(job.imageId(), job.imageUrl()))
                    .toList());
            repository.markPublished(jobs.stream().map(IndexingJobOutboxRepository.OutboxJob::imageId).toList());
        } catch (Exception exception) {
            long delaySeconds = Math.min(900L, 5L << Math.min(jobs.getFirst().retryCount(), 8));
            List<java.util.UUID> ids = jobs.stream().map(IndexingJobOutboxRepository.OutboxJob::imageId).toList();
            repository.markRetry(ids, exception.getMessage(), Duration.ofSeconds(delaySeconds));
            log.warn("Could not publish indexing outbox batch count={} reason={}", ids.size(), exception.getMessage());
        }
    }
}
