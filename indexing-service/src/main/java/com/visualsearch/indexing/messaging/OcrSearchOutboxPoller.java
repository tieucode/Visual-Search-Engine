package com.visualsearch.indexing.messaging;

import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.persistence.OcrSearchOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrSearchOutboxPoller {
    private final OcrSearchOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final IndexingProperties properties;

    @Scheduled(fixedDelayString = "${indexing.elasticsearch.ocr-poll-interval:5000}")
    public void poll() {
        int batchSize = properties.getElasticsearch().getOcrBatchSize();
        repository.enqueueMissing(batchSize);
        repository.enqueueDeleted(batchSize);
        repository.releaseStaleClaims(properties.getRabbit().getOcrClaimTimeout());
        for (var entry : repository.claimReady(batchSize)) {
            try {
                rabbitTemplate.convertAndSend(properties.getRabbit().getOcrSearchQueue(), entry.imageId().toString());
            } catch (Exception exception) {
                repository.releaseClaim(entry.imageId());
                log.warn("Could not publish OCR search job imageId={} reason={}", entry.imageId(), exception.getMessage());
            }
        }
    }
}
