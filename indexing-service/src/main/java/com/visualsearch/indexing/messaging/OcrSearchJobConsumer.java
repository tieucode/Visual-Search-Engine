package com.visualsearch.indexing.messaging;

import com.rabbitmq.client.Channel;
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
    private final OcrSearchOutboxRepository repository;
    private final ElasticsearchOcrIndexClient elasticsearch;

    @RabbitListener(queues = "${indexing.rabbit.ocr-search-queue}", containerFactory = "ocrSearchListenerContainerFactory")
    public void consume(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        UUID imageId;
        try {
            imageId = UUID.fromString(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8).replace("\"", ""));
        } catch (RuntimeException exception) {
            channel.basicAck(tag, false);
            log.warn("Discarded invalid OCR search job");
            return;
        }

        try {
            if (!repository.isProcessing(imageId)) {
                channel.basicAck(tag, false);
                return;
            }
            elasticsearch.initializeIndex();
            List<OcrIndexDocument> documents = repository.loadDocuments(List.of(imageId));
            if (documents.isEmpty()) {
                elasticsearch.delete(imageId.toString());
                repository.markDeleted(imageId);
            } else {
                elasticsearch.upsert(documents.get(0));
                repository.markIndexed(imageId);
            }
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            try {
                repository.markRetry(imageId, exception.getMessage(), Duration.ofSeconds(15));
                channel.basicAck(tag, false);
            } catch (Exception persistenceException) {
                log.error("Could not persist OCR search retry imageId={}", imageId, persistenceException);
                channel.basicNack(tag, false, true);
            }
        }
    }
}
