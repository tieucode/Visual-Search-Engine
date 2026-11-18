package com.visualsearch.indexing.messaging;

import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.persistence.OcrSearchOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrSearchOutboxPoller implements SchedulingConfigurer {
    private final OcrSearchOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final IndexingProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(this::poll,
                properties.getElasticsearch().getOcrPollInterval(), Duration.ZERO));
    }

    public void poll() {
        int batchSize = properties.getElasticsearch().getOcrBatchSize();
        repository.enqueueMissing(batchSize);
        repository.enqueueDeleted(batchSize);
        repository.releaseStaleClaims(properties.getRabbit().getOcrClaimTimeout());
        for (var entry : repository.claimReady(batchSize)) {
            try {
                rabbitTemplate.convertAndSend(properties.getRabbit().getOcrSearchQueue(), entry);
            } catch (Exception exception) {
                repository.releaseClaim(entry.imageId(), entry.revision());
                log.warn("Could not publish OCR search job imageId={} reason={}", entry.imageId(), exception.getMessage());
            }
        }
    }
}
