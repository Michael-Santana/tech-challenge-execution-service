package com.tech.challenger.execution.service.infrastructure.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tech.challenger.execution.service.application.ExecutionService;
import com.tech.challenger.execution.service.application.dto.BillingEvent;
import com.tech.challenger.execution.service.application.dto.OsEvent;
import org.junit.jupiter.api.Test;

class ExecutionEventListenerTest {

    @Test
    void forwardsIncomingOsAndBillingEvents() {
        ExecutionService service = mock(ExecutionService.class);
        ExecutionEventListener listener = new ExecutionEventListener(service);
        OsEvent osEvent = new OsEvent("os-1", 1L, "ORDER_CREATED", "RECEBIDA", "now");
        BillingEvent billingEvent = new BillingEvent("bill-1", "PAYMENT_APPROVED", 1L, "budget-1", "now");

        listener.receiveOsEvent(osEvent);
        listener.receiveBillingEvent(billingEvent);

        verify(service).handleOsEvent(osEvent);
        verify(service).handleBillingEvent(billingEvent);
    }
}
