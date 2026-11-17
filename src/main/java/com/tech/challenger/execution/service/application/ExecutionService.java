package com.tech.challenger.execution.service.application;

import com.tech.challenger.execution.service.application.dto.BillingEvent;
import com.tech.challenger.execution.service.application.dto.ExecutionResponse;
import com.tech.challenger.execution.service.application.dto.OsEvent;
import com.tech.challenger.execution.service.domain.ExecutionEvent;
import com.tech.challenger.execution.service.domain.ExecutionStatus;
import com.tech.challenger.execution.service.domain.ExecutionWorkOrder;
import com.tech.challenger.execution.service.domain.ExecutionWorkOrderRepository;
import com.tech.challenger.execution.service.domain.RepairRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
    private final ExecutionWorkOrderRepository workOrders;

    public ExecutionService(ExecutionWorkOrderRepository workOrders) {
        this.workOrders = workOrders;
    }

    public synchronized void handleOsEvent(OsEvent event) {
        if (event == null || event.orderId() == null || event.eventType() == null) {
            throw new IllegalArgumentException("Evento da OS sem orderId ou eventType");
        }
        boolean opened = "ORDER_CREATED".equals(event.eventType()) || "OS_OPENED".equals(event.eventType());
        boolean cancelled = "STATUS_UPDATED".equals(event.eventType()) && "CANCELADA".equals(event.newStatus());
        if (!opened && !cancelled) {
            return;
        }
        String eventId = event.id() != null ? event.id() : event.orderId() + ":" + event.eventType();
        ExecutionWorkOrder execution = getOrCreate(event.orderId());
        if (execution.getProcessedEventIds().add(eventId)) {
            if (cancelled) {
                cancel(execution);
            }
            execution.setUpdatedAt(Instant.now());
            workOrders.save(execution);
        }
    }

    public synchronized void handleBillingEvent(BillingEvent event) {
        if (event == null || event.orderId() == null || event.eventType() == null) {
            throw new IllegalArgumentException("Evento de cobranca sem orderId ou eventType");
        }
        boolean approved = "PAYMENT_APPROVED".equals(event.eventType());
        boolean rejected = "BUDGET_REJECTED".equals(event.eventType());
        if (!approved && !rejected) {
            return;
        }
        String eventId = event.eventId() != null
                ? event.eventId()
                : event.orderId() + ":" + event.eventType() + ":" + event.budgetId();
        ExecutionWorkOrder execution = getOrCreate(event.orderId());
        if (!execution.getProcessedEventIds().add(eventId)) {
            return;
        }
        if (rejected) {
            cancel(execution);
        } else if (execution.getStatus() == ExecutionStatus.CANCELLED) {
            execution.setPaymentApproved(true);
            execution.setRefundRequired(true);
            log.warn("Pagamento recebido para OS cancelada {}; estorno manual necessario", event.orderId());
        } else {
            execution.setPaymentApproved(true);
            if (execution.getStatus() == ExecutionStatus.WAITING_FOR_APPROVAL) {
                queueForRepair(execution);
            }
        }
        execution.setUpdatedAt(Instant.now());
        workOrders.save(execution);
    }

    public synchronized ExecutionResponse startDiagnosis(Long orderId) {
        ExecutionWorkOrder execution = requireOrder(orderId);
        requireStatus(execution, ExecutionStatus.WAITING_FOR_DIAGNOSIS);
        execution.setStatus(ExecutionStatus.IN_DIAGNOSIS);
        execution.setUpdatedAt(Instant.now());
        addEvent(execution, "EXECUTION_DIAGNOSIS_STARTED", "EM_DIAGNOSTICO",
                "Diagnostico da OS iniciado");
        return toResponse(workOrders.save(execution), null);
    }

    public synchronized ExecutionResponse recordDiagnosis(Long orderId, String diagnosis) {
        ExecutionWorkOrder execution = requireOrder(orderId);
        requireStatus(execution, ExecutionStatus.IN_DIAGNOSIS);
        execution.setDiagnosis(diagnosis.trim());
        execution.setStatus(ExecutionStatus.WAITING_FOR_APPROVAL);
        if (execution.isPaymentApproved()) {
            queueForRepair(execution);
        }
        execution.setUpdatedAt(Instant.now());
        addEvent(execution, "EXECUTION_DIAGNOSIS_COMPLETED", "AGUARDANDO_APROVACAO",
                "Diagnostico da OS concluido");
        return toResponse(workOrders.save(execution), null);
    }

    public synchronized ExecutionResponse startRepairs(Long orderId) {
        ExecutionWorkOrder execution = requireOrder(orderId);
        requireStatus(execution, ExecutionStatus.QUEUED_FOR_REPAIR);
        execution.setStatus(ExecutionStatus.IN_PROGRESS);
        execution.setStartedAt(Instant.now());
        execution.setUpdatedAt(execution.getStartedAt());
        addEvent(execution, "EXECUTION_STARTED", "EM_EXECUCAO", "Reparos da OS iniciados");
        return toResponse(workOrders.save(execution), null);
    }

    public synchronized ExecutionResponse recordRepair(Long orderId, String description) {
        ExecutionWorkOrder execution = requireOrder(orderId);
        requireStatus(execution, ExecutionStatus.IN_PROGRESS);
        execution.getRepairs().add(new RepairRecord(description.trim(), Instant.now()));
        execution.setUpdatedAt(Instant.now());
        return toResponse(workOrders.save(execution), null);
    }

    public synchronized ExecutionResponse complete(Long orderId) {
        ExecutionWorkOrder execution = requireOrder(orderId);
        requireStatus(execution, ExecutionStatus.IN_PROGRESS);
        if (execution.getRepairs().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Registre ao menos um reparo antes de concluir a execucao");
        }
        execution.setStatus(ExecutionStatus.COMPLETED);
        execution.setCompletedAt(Instant.now());
        execution.setUpdatedAt(execution.getCompletedAt());
        addEvent(execution, "EXECUTION_COMPLETED", "FINALIZADA", "Execucao da OS concluida");
        return toResponse(workOrders.save(execution), null);
    }

    public synchronized ExecutionResponse findByOrderId(Long orderId) {
        return toResponse(requireOrder(orderId), queuePosition(orderId));
    }

    public synchronized List<ExecutionResponse> findQueue() {
        List<ExecutionWorkOrder> queue =
                workOrders.findByStatusOrderByQueuedAtAsc(ExecutionStatus.QUEUED_FOR_REPAIR);
        return java.util.stream.IntStream.range(0, queue.size())
                .mapToObj(index -> toResponse(queue.get(index), index + 1))
                .toList();
    }

    private ExecutionWorkOrder getOrCreate(Long orderId) {
        return workOrders.findById(orderId.toString())
                .orElseGet(() -> workOrders.save(new ExecutionWorkOrder(orderId, Instant.now())));
    }

    private ExecutionWorkOrder requireOrder(Long orderId) {
        return workOrders.findById(orderId.toString())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "OS ainda nao foi recebida pelo Execution Service"));
    }

    private void requireStatus(ExecutionWorkOrder execution, ExecutionStatus expected) {
        if (execution.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Estado esperado " + expected + ", estado atual " + execution.getStatus());
        }
    }

    private void queueForRepair(ExecutionWorkOrder execution) {
        execution.setStatus(ExecutionStatus.QUEUED_FOR_REPAIR);
        execution.setQueuedAt(Instant.now());
    }

    private void cancel(ExecutionWorkOrder execution) {
        if (execution.getStatus() == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("Cancelamento de reparo concluido requer tratamento manual");
        }
        execution.setRefundRequired(execution.isPaymentApproved());
        if (execution.isRefundRequired()) {
            log.warn("OS {} cancelada apos pagamento; estorno manual necessario", execution.getOrderId());
        }
        execution.setStatus(ExecutionStatus.CANCELLED);
    }

    private void addEvent(ExecutionWorkOrder execution, String eventType, String osStatus, String description) {
        execution.addPendingEvent(new ExecutionEvent(
                UUID.randomUUID().toString(), eventType, execution.getOrderId(),
                osStatus, description, Instant.now(), false));
    }

    private Integer queuePosition(Long orderId) {
        List<ExecutionWorkOrder> queue =
                workOrders.findByStatusOrderByQueuedAtAsc(ExecutionStatus.QUEUED_FOR_REPAIR);
        for (int index = 0; index < queue.size(); index++) {
            if (orderId.equals(queue.get(index).getOrderId())) {
                return index + 1;
            }
        }
        return null;
    }

    private ExecutionResponse toResponse(ExecutionWorkOrder execution, Integer queuePosition) {
        return new ExecutionResponse(
                execution.getOrderId(), execution.getStatus(), execution.getDiagnosis(),
                execution.isPaymentApproved(), execution.isRefundRequired(), execution.getCreatedAt(), execution.getUpdatedAt(),
                execution.getQueuedAt(), execution.getStartedAt(), execution.getCompletedAt(),
                List.copyOf(execution.getRepairs()), queuePosition);
    }
}
