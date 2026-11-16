package com.tech.challenger.execution.service.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

class RabbitMqConfigurationTest {

    private final RabbitMqConfiguration configuration = new RabbitMqConfiguration();

    @Test
    void declaresExchangesQueuesAndBindings() {
        TopicExchange os = configuration.osExchange("os.exchange");
        TopicExchange billing = configuration.billingExchange("billing.exchange");
        TopicExchange dlx = configuration.deadLetterExchange("execution.dlx");
        Queue osQueue = configuration.osEventsQueue("os-events", "execution.dlx");
        Queue billingQueue = configuration.billingEventsQueue("billing-events", "execution.dlx");
        Queue dlq = configuration.deadLetterQueue("dead-events");

        Binding osBinding = configuration.osEventsBinding(osQueue, os, "os.event");
        Binding billingBinding = configuration.billingEventsBinding(billingQueue, billing, "billing.payment_approved");
        Binding dlqBinding = configuration.deadLetterBinding(dlq, dlx);

        assertThat(os.getName()).isEqualTo("os.exchange");
        assertThat(osBinding.getRoutingKey()).isEqualTo("os.event");
        assertThat(billingBinding.getRoutingKey()).isEqualTo("billing.payment_approved");
        assertThat(dlqBinding.getRoutingKey()).isEqualTo("execution.dead");
        assertThat(configuration.messageConverter()).isInstanceOf(MessageConverter.class);
        assertThat(configuration.rabbitRetryInterceptor()).isInstanceOf(RetryOperationsInterceptor.class);
    }

    @Test
    void configuresRabbitListenerFactoryWithRetriesAndJson() {
        SimpleRabbitListenerContainerFactoryConfigurer configurer =
                mock(SimpleRabbitListenerContainerFactoryConfigurer.class);
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RetryOperationsInterceptor retry = configuration.rabbitRetryInterceptor();
        MessageConverter converter = configuration.messageConverter();

        SimpleRabbitListenerContainerFactory factory = configuration.rabbitListenerContainerFactory(
                configurer, connectionFactory, retry, converter);

        verify(configurer).configure(factory, connectionFactory);
        assertThat(factory).isNotNull();
    }
}
