package com.visualsearch.indexing.messaging;

import com.rabbitmq.client.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualsearch.indexing.persistence.OcrSearchOutboxRepository.OutboxEntry;
import com.visualsearch.indexing.client.elasticsearch.ElasticsearchOcrIndexClient;
import com.visualsearch.indexing.persistence.OcrIndexDocument;
import com.visualsearch.indexing.persistence.OcrSearchOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrSearchJobConsumer {
    private static final long LEGACY_REVISION = 1L << 40;

    private final OcrSearchOutboxRepository repository;
    private final ElasticsearchOcrIndexClient elasticsearch;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${indexing.rabbit.ocr-search-queue}", containerFactory = "ocrSearchListenerContainerFactory")
    public void consume(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        OutboxEntry job;
        try {
            var payload = objectMapper.readTree(message.getBody());
            if (payload != null && payload.isTextual()) {
                // The previous publisher sent a JSON UUID. Only release an unmigrated claim.
                job = new OutboxEntry(UUID.fromString(payload.textValue()), LEGACY_REVISION);
            } else {
                job = objectMapper.treeToValue(payload, OutboxEntry.class);
                if (job == null || job.imageId() == null || job.revision() <= 0) {
                    throw new IllegalArgumentException("Missing OCR job identity");
                }
            }
        } catch (IOException | RuntimeException invalid) {
            channel.basicAck(tag, false);
            log.warn("Discarded invalid OCR search job");
            return;
        }
        UUID imageId = job.imageId();
        long revision = job.revision();
        try {
            if (revision == LEGACY_REVISION) {
                repository.releaseClaim(imageId, revision);
                channel.basicAck(tag, false);
                return;
            }
            if (!repository.isProcessing(imageId, revision)) {
                channel.basicAck(tag, false);
                return;
            }
            List<OcrIndexDocument> documents = repository.loadDocuments(List.of(imageId));
            if (documents.isEmpty()) {
                elasticsearch.markDeleted(imageId, revision);
                repository.markDeleted(imageId, revision);
            } else {
                elasticsearch.upsert(documents.get(0), revision);
                repository.markIndexed(imageId, revision);
            }
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            try {
                repository.markRetry(imageId, revision, exception.getMessage(), Duration.ofSeconds(15));
                channel.basicAck(tag, false);
            } catch (Exception persistenceException) {
                log.error("Could not persist OCR search retry imageId={}", imageId, persistenceException);
                channel.basicNack(tag, false, true);
            }
        }
    }
}
