package com.visualsearch.indexing.messaging;

import com.visualsearch.indexing.config.IndexingProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class IndexingMessagePublisher {

    public static final String RETRY_ATTEMPT_HEADER = "x-retry-attempt";

    private final RabbitTemplate rabbitTemplate;
    private final IndexingProperties.Rabbit properties;

    public IndexingMessagePublisher(RabbitTemplate rabbitTemplate, IndexingProperties indexingProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = indexingProperties.getRabbit();
    }

    public void publishRetry(int attempt, List<ImageIndexingMessage.ImageItem> items) {
        ImageIndexingMessage retryMessage = new ImageIndexingMessage(items);
        publishConfirmed(
                properties.getRetryExchange(),
                properties.getRetryRoutingKey(),
                retryMessage,
                message -> {
                    message.getMessageProperties().setHeader(RETRY_ATTEMPT_HEADER, attempt);
                    return message;
                });
    }

    public void publishDeadLetter(byte[] originalBody, String reason) {
        Message message = MessageBuilder.withBody(originalBody)
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("x-error-message", sanitize(reason))
                .build();
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.send(
                properties.getDeadLetterExchange(),
                properties.getDeadLetterRoutingKey(),
                message,
                correlationData);
        awaitConfirm(correlationData);
    }

    private void publishConfirmed(
            String exchange,
            String routingKey,
            Object payload,
            org.springframework.amqp.core.MessagePostProcessor postProcessor) {
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, postProcessor, correlationData);
        awaitConfirm(correlationData);
    }

    private void awaitConfirm(CorrelationData correlationData) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(properties.getPublisherConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ rejected published message: " + confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ could not route published message");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ publisher confirm", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not confirm RabbitMQ publication", exception);
        }
    }

    private String sanitize(String value) {
        String message = value == null ? "Invalid indexing message" : value;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
