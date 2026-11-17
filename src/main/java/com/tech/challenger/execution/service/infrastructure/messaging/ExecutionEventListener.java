package com.tech.challenger.execution.service.infrastructure.messaging;

import com.tech.challenger.execution.service.application.ExecutionService;
import com.tech.challenger.execution.service.application.dto.BillingEvent;
import com.tech.challenger.execution.service.application.dto.OsEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ExecutionEventListener {

    private final ExecutionService executionService;

    public ExecutionEventListener(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @RabbitListener(queues = "${app.messaging.os-events-queue}")
    public void receiveOsEvent(OsEvent event) {
        executionService.handleOsEvent(event);
    }

    @RabbitListener(queues = "${app.messaging.billing-events-queue}")
    public void receiveBillingEvent(BillingEvent event) {
        executionService.handleBillingEvent(event);
    }
}
