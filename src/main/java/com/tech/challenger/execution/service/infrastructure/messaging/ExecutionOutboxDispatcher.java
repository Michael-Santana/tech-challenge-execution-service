package com.tech.challenger.execution.service.infrastructure.messaging;

import com.tech.challenger.execution.service.domain.ExecutionEvent;
import com.tech.challenger.execution.service.domain.ExecutionWorkOrder;
import com.tech.challenger.execution.service.domain.ExecutionWorkOrderRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExecutionOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ExecutionOutboxDispatcher.class);

    private final ExecutionWorkOrderRepository workOrders;
    private final MongoTemplate mongoTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public ExecutionOutboxDispatcher(
            ExecutionWorkOrderRepository workOrders,
            MongoTemplate mongoTemplate,
            RabbitTemplate rabbitTemplate,
            @Value("${app.messaging.os-exchange}") String exchange,
            @Value("${app.messaging.os-routing-key}") String routingKey) {
        this.workOrders = workOrders;
        this.mongoTemplate = mongoTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.rabbitTemplate.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${app.outbox.delay-ms}")
    public void dispatchPendingEvents() {
        List<ExecutionWorkOrder> work = workOrders.findWithUnpublishedEvents();
        for (ExecutionWorkOrder order : work) {
            for (ExecutionEvent event : order.getPendingEvents()) {
                if (!event.isPublished()) {
                    try {
                        publish(event);
                        markPublished(order.getId(), event.getEventId());
                    } catch (RuntimeException exception) {
                        log.error("Falha ao publicar evento {} da OS {}: {}",
                                event.getEventId(), event.getOrderId(), exception.getMessage(), exception);
                        break;
                    }
                }
            }
        }
    }

    private void publish(ExecutionEvent event) {
        CorrelationData correlation = new CorrelationData(event.getEventId());
        rabbitTemplate.convertAndSend(exchange, routingKey, event, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ nao confirmou a entrega: " + confirm.getReason());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicacao interrompida", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("Falha aguardando confirmacao do RabbitMQ", exception);
        }
    }

    private void markPublished(String orderId, String eventId) {
        Query query = Query.query(Criteria.where("_id").is(orderId)
                .and("pendingEvents.eventId").is(eventId)
                .and("pendingEvents.published").is(false));
        Update update = new Update().set("pendingEvents.$.published", true);
        mongoTemplate.updateFirst(query, update, ExecutionWorkOrder.class);
    }
}
