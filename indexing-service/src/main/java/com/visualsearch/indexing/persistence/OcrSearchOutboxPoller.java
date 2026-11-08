package com.visualsearch.indexing.persistence;

import com.visualsearch.indexing.client.elasticsearch.ElasticsearchOcrIndexClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrSearchOutboxPoller {
    private final OcrSearchOutboxRepository repository;
    private final ElasticsearchOcrIndexClient elasticsearch;
    private final com.visualsearch.indexing.config.IndexingProperties properties;

    @Scheduled(fixedDelayString = "${indexing.elasticsearch.ocr-poll-interval:5000}")
    public void poll() {
        int batchSize = properties.getElasticsearch().getOcrBatchSize();
        elasticsearch.initializeIndex();
        repository.enqueueMissing(batchSize);
        repository.enqueueDeleted(batchSize);
        List<OcrSearchOutboxRepository.OutboxEntry> entries = repository.findReady(batchSize);
        if (entries.isEmpty()) return;
        List<UUID> ids = entries.stream().map(OcrSearchOutboxRepository.OutboxEntry::imageId).toList();
        Map<UUID, OcrIndexDocument> documents = new HashMap<>();
        repository.loadDocuments(ids).forEach(document -> documents.put(document.imageId(), document));

        for (var entry : entries) {
            try {
                OcrIndexDocument document = documents.get(entry.imageId());
                if (document == null) {
                    elasticsearch.delete(entry.imageId().toString());
                    repository.markDeleted(entry.imageId());
                    continue;
                }
                else elasticsearch.upsert(document);
                repository.markIndexed(entry.imageId());
            } catch (Exception exception) {
                long delaySeconds = Math.min(900L, 5L << Math.min(entry.retryCount(), 8));
                repository.markRetry(entry.imageId(), exception.getMessage(), Duration.ofSeconds(delaySeconds));
                log.warn("OCR Elasticsearch sync failed imageId={} retryCount={} reason={}",
                        entry.imageId(), entry.retryCount() + 1, exception.getMessage());
            }
        }
    }
}
