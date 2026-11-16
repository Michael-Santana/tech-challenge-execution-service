package com.tech.challenger.execution.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.tech.challenger.execution.service.application.dto.BillingEvent;
import com.tech.challenger.execution.service.application.dto.OsEvent;
import com.tech.challenger.execution.service.domain.ExecutionStatus;
import com.tech.challenger.execution.service.domain.ExecutionWorkOrder;
import com.tech.challenger.execution.service.domain.ExecutionWorkOrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ExecutionServiceTest {

    private final AtomicReference<ExecutionWorkOrder> stored = new AtomicReference<>();
    private ExecutionService service;
    private ExecutionWorkOrderRepository workOrders;

    @BeforeEach
    void setUp() {
        stored.set(null);
        workOrders = org.mockito.Mockito.mock(ExecutionWorkOrderRepository.class);
        when(workOrders.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(workOrders.save(any())).thenAnswer(invocation -> {
            ExecutionWorkOrder order = invocation.getArgument(0);
            stored.set(order);
            return order;
        });
        when(workOrders.findByStatusOrderByQueuedAtAsc(ExecutionStatus.QUEUED_FOR_REPAIR))
                .thenAnswer(invocation -> stored.get() != null
                        && stored.get().getStatus() == ExecutionStatus.QUEUED_FOR_REPAIR
                        ? List.of(stored.get()) : List.of());
        service = new ExecutionService(workOrders);
    }

    @Test
    void completesDiagnosisPaymentAndRepairFlowAndCreatesOutboxEvents() {
        service.handleOsEvent(new OsEvent("os-event-1", 42L, "ORDER_CREATED", "RECEBIDA", "now"));

        assertThat(service.findByOrderId(42L).status()).isEqualTo(ExecutionStatus.WAITING_FOR_DIAGNOSIS);
        service.startDiagnosis(42L);
        service.recordDiagnosis(42L, "  Substituir a correia  ");
        assertThat(service.findByOrderId(42L).status()).isEqualTo(ExecutionStatus.WAITING_FOR_APPROVAL);

        service.handleBillingEvent(new BillingEvent("payment-event-1", "PAYMENT_APPROVED", 42L, "budget-1", "now"));
        assertThat(service.findQueue()).singleElement()
                .satisfies(item -> assertThat(item.queuePosition()).isEqualTo(1));
        service.startRepairs(42L);
        service.recordRepair(42L, "  Correia substituida  ");
        var completed = service.complete(42L);

        assertThat(completed.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(completed.diagnosis()).isEqualTo("Substituir a correia");
        assertThat(completed.repairs()).singleElement()
                .satisfies(repair -> assertThat(repair.description()).isEqualTo("Correia substituida"));
        assertThat(stored.get().getPendingEvents()).extracting("status")
                .containsExactly("EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "EM_EXECUCAO", "FINALIZADA");
    }

    @Test
    void paymentArrivingBeforeOrderOrDiagnosisIsRememberedAndIdempotent() {
        BillingEvent payment = new BillingEvent("payment-event-2", "PAYMENT_APPROVED", 52L, "budget-2", "now");
        service.handleBillingEvent(payment);
        service.handleBillingEvent(payment);
        service.handleOsEvent(new OsEvent("os-event-2", 52L, "ORDER_CREATED", "RECEBIDA", "now"));

        service.startDiagnosis(52L);
        var result = service.recordDiagnosis(52L, "Freios desgastados");

        assertThat(result.status()).isEqualTo(ExecutionStatus.QUEUED_FOR_REPAIR);
        assertThat(result.paymentApproved()).isTrue();
        assertThat(service.findQueue()).hasSize(1);
    }

    @Test
    void ignoresUnrelatedEventsAndRejectsActionsOutOfOrder() {
        service.handleOsEvent(new OsEvent("ignored", 62L, "STATUS_UPDATED", "RECEBIDA", "now"));
        service.handleBillingEvent(new BillingEvent("ignored", "BUDGET_CREATED", 62L, "budget-3", "now"));

        assertThatThrownBy(() -> service.startDiagnosis(62L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("OS ainda nao foi recebida");

        service.handleOsEvent(new OsEvent("os-event-3", 62L, "ORDER_CREATED", "RECEBIDA", "now"));
        assertThatThrownBy(() -> service.startRepairs(62L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Estado esperado");
        assertThat(service.findQueue()).isEmpty();
    }

    @Test
    void rejectsMalformedMessagesAndDeduplicatesOrderEvents() {
        assertThatThrownBy(() -> service.handleOsEvent(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.handleOsEvent(new OsEvent("missing-order", null, "ORDER_CREATED", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.handleBillingEvent(
                new BillingEvent("missing-order", "PAYMENT_APPROVED", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        OsEvent opened = new OsEvent("os-event-5", 82L, "ORDER_CREATED", "RECEBIDA", "now");
        service.handleOsEvent(opened);
        service.handleOsEvent(opened);

        assertThat(service.findByOrderId(82L).status()).isEqualTo(ExecutionStatus.WAITING_FOR_DIAGNOSIS);
    }

    @Test
    void cannotCompleteRepairWithoutRecordingWork() {
        service.handleOsEvent(new OsEvent("os-event-4", 72L, "ORDER_CREATED", "RECEBIDA", "now"));
        service.startDiagnosis(72L);
        service.recordDiagnosis(72L, "Reparo simples");
        service.handleBillingEvent(new BillingEvent("payment-event-4", "PAYMENT_APPROVED", 72L, "budget-4", "now"));
        service.startRepairs(72L);

        assertThatThrownBy(() -> service.complete(72L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ao menos um reparo");
        assertThat(service.findByOrderId(72L).status()).isEqualTo(ExecutionStatus.IN_PROGRESS);
    }
}
