package com.visualsearch.indexing.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMqConfiguration {

    @Bean
    DirectExchange indexingExchange(IndexingProperties properties) {
        return new DirectExchange(properties.getRabbit().getExchange(), true, false);
    }

    @Bean
    Queue indexingQueue(IndexingProperties properties) {
        return QueueBuilder.durable(properties.getRabbit().getQueue()).build();
    }

    @Bean
    Binding indexingBinding(Queue indexingQueue, DirectExchange indexingExchange, IndexingProperties properties) {
        return BindingBuilder.bind(indexingQueue)
                .to(indexingExchange)
                .with(properties.getRabbit().getRoutingKey());
    }

    @Bean
    DirectExchange retryExchange(IndexingProperties properties) {
        return new DirectExchange(properties.getRabbit().getRetryExchange(), true, false);
    }

    @Bean
    Queue retryQueue(IndexingProperties properties) {
        IndexingProperties.Rabbit rabbit = properties.getRabbit();
        return QueueBuilder.durable(rabbit.getRetryQueue())
                .withArgument("x-message-ttl", rabbit.getRetryDelay().toMillis())
                .deadLetterExchange(rabbit.getExchange())
                .deadLetterRoutingKey(rabbit.getRoutingKey())
                .build();
    }

    @Bean
    Binding retryBinding(Queue retryQueue, DirectExchange retryExchange, IndexingProperties properties) {
        return BindingBuilder.bind(retryQueue)
                .to(retryExchange)
                .with(properties.getRabbit().getRetryRoutingKey());
    }

    @Bean
    DirectExchange deadLetterExchange(IndexingProperties properties) {
        return new DirectExchange(properties.getRabbit().getDeadLetterExchange(), true, false);
    }

    @Bean
    Queue deadLetterQueue(IndexingProperties properties) {
        return QueueBuilder.durable(properties.getRabbit().getDeadLetterQueue()).build();
    }

    @Bean
    Binding deadLetterBinding(
            Queue deadLetterQueue,
            DirectExchange deadLetterExchange,
            IndexingProperties properties) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.getRabbit().getDeadLetterRoutingKey());
    }

    @Bean
    MessageConverter indexingJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate indexingRabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter indexingJsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(indexingJsonMessageConverter);
        template.setMandatory(true);
        template.setBeforePublishPostProcessors(message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
        template.setReturnsCallback(returned -> log.error(
                "RabbitMQ returned message exchange={} routingKey={} replyCode={} replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()));
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory indexingListenerContainerFactory(
            ConnectionFactory connectionFactory,
            IndexingProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setContainerCustomizer(
                container -> container.setShutdownTimeout(properties.getRabbit().getShutdownTimeout().toMillis()));
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
