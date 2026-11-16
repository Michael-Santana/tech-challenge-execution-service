package com.tech.challenger.execution.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExecutionDomainTest {

    @Test
    void storesWorkOrderAndTracksEventPublication() {
        Instant now = Instant.now();
        ExecutionWorkOrder order = new ExecutionWorkOrder(21L, now);
        ExecutionEvent event = new ExecutionEvent(
                "event-21", "EXECUTION_STARTED", 21L, "EM_EXECUCAO", "Iniciado", now, false);
        order.setStatus(ExecutionStatus.IN_PROGRESS);
        order.setDiagnosis("Defeito");
        order.setPaymentApproved(true);
        order.setUpdatedAt(now);
        order.setQueuedAt(now);
        order.setStartedAt(now);
        order.setCompletedAt(now);
        order.getRepairs().add(new RepairRecord("Troca", now));
        order.getProcessedEventIds().add("input-21");
        order.addPendingEvent(event);

        assertThat(order.getId()).isEqualTo("21");
        assertThat(order.getOrderId()).isEqualTo(21L);
        assertThat(order.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
        assertThat(order.getDiagnosis()).isEqualTo("Defeito");
        assertThat(order.isPaymentApproved()).isTrue();
        assertThat(order.getCreatedAt()).isEqualTo(now);
        assertThat(order.getUpdatedAt()).isEqualTo(now);
        assertThat(order.getQueuedAt()).isEqualTo(now);
        assertThat(order.getStartedAt()).isEqualTo(now);
        assertThat(order.getCompletedAt()).isEqualTo(now);
        assertThat(order.getRepairs()).containsExactly(new RepairRecord("Troca", now));
        assertThat(order.getProcessedEventIds()).containsExactly("input-21");
        assertThat(order.getPendingEvents()).containsExactly(event);

        assertThat(event.getEventId()).isEqualTo("event-21");
        assertThat(event.getEventType()).isEqualTo("EXECUTION_STARTED");
        assertThat(event.getOrderId()).isEqualTo(21L);
        assertThat(event.getStatus()).isEqualTo("EM_EXECUCAO");
        assertThat(event.getDescription()).isEqualTo("Iniciado");
        assertThat(event.getOccurredAt()).isEqualTo(now);
        assertThat(event.isPublished()).isFalse();
        event.markPublished();
        assertThat(event.isPublished()).isTrue();
    }
}
