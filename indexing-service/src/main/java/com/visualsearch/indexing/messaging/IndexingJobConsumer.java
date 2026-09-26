package com.visualsearch.indexing.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.visualsearch.indexing.exception.InvalidIndexingMessageException;
import com.visualsearch.indexing.processing.IndexingProcessor;
import com.visualsearch.indexing.processing.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexingJobConsumer {

    private final ObjectMapper objectMapper;
    private final IndexingProcessor indexingProcessor;
    private final IndexingMessagePublisher messagePublisher;

    @RabbitListener(
            queues = "${indexing.rabbit.queue}",
            containerFactory = "indexingListenerContainerFactory")
    public void consume(Message rawMessage, Channel channel) throws IOException {
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        ImageIndexingMessage message = null;
        int currentAttempt = 0;
        try {
            message = objectMapper.readValue(
                    rawMessage.getBody(), ImageIndexingMessage.class);
            currentAttempt = retryAttempt(rawMessage);
            log.info("Received indexing message imageCount={} retryAttempt={} redelivered={}",
                    message.images().size(),
                    currentAttempt,
                    rawMessage.getMessageProperties().isRedelivered());

            ProcessingResult result = indexingProcessor.process(message, currentAttempt);
            for (var retryGroup : result.retryGroups().entrySet()) {
                messagePublisher.publishRetry(retryGroup.getKey(), retryGroup.getValue());
            }

            channel.basicAck(deliveryTag, false);
            log.info("Acknowledged indexing message success={} failed={} skipped={} retryGroups={}",
                    result.succeeded(), result.failed(), result.skipped(), result.retryGroups().keySet());
        } catch (InvalidIndexingMessageException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            try {
                messagePublisher.publishDeadLetter(rawMessage.getBody(), exception.getMessage());
                channel.basicAck(deliveryTag, false);
                log.error("Moved invalid indexing message to DLQ reason={}", exception.getMessage());
            } catch (RuntimeException publishException) {
                log.error("Could not publish invalid message to DLQ; requeueing", publishException);
                channel.basicNack(deliveryTag, false, true);
            }
        } catch (TransientDataAccessException
                 | DataAccessResourceFailureException
                 | CannotCreateTransactionException exception) {
            if (message != null) {
                try {
                    messagePublisher.publishRetry(currentAttempt, message.images());
                    channel.basicAck(deliveryTag, false);
                    log.warn("Database temporarily unavailable; delayed message retryAttempt={} reason={}",
                            currentAttempt, exception.getMessage());
                    return;
                } catch (RuntimeException publishException) {
                    log.error("Could not publish delayed retry after database failure", publishException);
                }
            }
            channel.basicNack(deliveryTag, false, true);
        } catch (Exception exception) {
            log.error("Indexing message processing failed before a durable retry was confirmed", exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private int retryAttempt(Message message) {
        Object value = message.getMessageProperties()
                .getHeaders()
                .get(IndexingMessagePublisher.RETRY_ATTEMPT_HEADER);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            throw new InvalidIndexingMessageException("Invalid retry attempt header");
        }
    }
}
