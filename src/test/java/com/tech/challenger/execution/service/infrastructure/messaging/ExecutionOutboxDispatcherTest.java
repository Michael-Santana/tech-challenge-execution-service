package com.tech.challenger.execution.service.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tech.challenger.execution.service.domain.ExecutionEvent;
import com.tech.challenger.execution.service.domain.ExecutionWorkOrder;
import com.tech.challenger.execution.service.domain.ExecutionWorkOrderRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;

class ExecutionOutboxDispatcherTest {

    @Test
    void publishesPendingEventAndMarksItAsSent() {
        ExecutionWorkOrderRepository repository = mock(ExecutionWorkOrderRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ExecutionWorkOrder order = new ExecutionWorkOrder(12L, Instant.now());
        order.addPendingEvent(new ExecutionEvent(
                "event-12", "EXECUTION_STARTED", 12L, "EM_EXECUCAO", "Iniciado", Instant.now(), false));
        when(repository.findWithUnpublishedEvents()).thenReturn(List.of(order));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        new ExecutionOutboxDispatcher(repository, mongoTemplate, rabbitTemplate, "os.exchangeee", "execution.status")
                .dispatchPendingEvents();

        verify(mongoTemplate).updateFirst(any(), any(), any(Class.class));
    }

    @Test
    void leavesEventPendingWhenBrokerPublicationFails() {
        ExecutionWorkOrderRepository repository = mock(ExecutionWorkOrderRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ExecutionWorkOrder order = new ExecutionWorkOrder(13L, Instant.now());
        order.addPendingEvent(new ExecutionEvent(
                "event-13", "EXECUTION_COMPLETED", 13L, "FINALIZADA", "Concluido", Instant.now(), false));
        when(repository.findWithUnpublishedEvents()).thenReturn(List.of(order));
        doThrow(new IllegalStateException("broker indisponivel")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        new ExecutionOutboxDispatcher(repository, mongoTemplate, rabbitTemplate, "os.exchangeee", "execution.status")
                .dispatchPendingEvents();

        if (order.getPendingEvents().getFirst().isPublished()) {
            throw new AssertionError("Evento nao deveria ser marcado como publicado");
        }
    }
}
