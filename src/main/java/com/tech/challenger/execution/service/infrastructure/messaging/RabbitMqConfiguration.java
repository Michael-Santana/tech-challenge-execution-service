package com.tech.challenger.execution.service.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class RabbitMqConfiguration {

    @Bean
    TopicExchange osExchange(@Value("${app.messaging.os-exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    TopicExchange billingExchange(@Value("${app.messaging.billing-exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    TopicExchange deadLetterExchange(@Value("${app.messaging.dead-letter-exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    Queue osEventsQueue(
            @Value("${app.messaging.os-events-queue}") String queue,
            @Value("${app.messaging.dead-letter-exchange}") String deadLetterExchange) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey("execution.dead")
                .build();
    }

    @Bean
    Queue billingEventsQueue(
            @Value("${app.messaging.billing-events-queue}") String queue,
            @Value("${app.messaging.dead-letter-exchange}") String deadLetterExchange) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey("execution.dead")
                .build();
    }

    @Bean
    Queue deadLetterQueue(@Value("${app.messaging.dead-letter-queue}") String queue) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    Binding osEventsBinding(
            Queue osEventsQueue, TopicExchange osExchange,
            @Value("${app.messaging.os-events-routing-key}") String routingKey) {
        return BindingBuilder.bind(osEventsQueue).to(osExchange).with(routingKey);
    }

    @Bean
    Binding billingEventsBinding(
            Queue billingEventsQueue, TopicExchange billingExchange,
            @Value("${app.messaging.payment-approved-routing-key}") String routingKey) {
        return BindingBuilder.bind(billingEventsQueue).to(billingExchange).with(routingKey);
    }

    @Bean
    Binding budgetRejectedBinding(Queue billingEventsQueue, TopicExchange billingExchange,
            @Value("${app.messaging.budget-rejected-routing-key}") String routingKey) {
        return BindingBuilder.bind(billingEventsQueue).to(billingExchange).with(routingKey);
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("execution.dead");
    }

    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RetryOperationsInterceptor rabbitRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(5)
                .backOffOptions(1_000, 2.0, 10_000)
                .recoverer(new org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor rabbitRetryInterceptor,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(rabbitRetryInterceptor);
        return factory;
    }
}
