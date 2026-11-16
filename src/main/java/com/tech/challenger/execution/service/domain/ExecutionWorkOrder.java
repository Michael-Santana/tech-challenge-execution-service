package com.tech.challenger.execution.service.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("execution_work_orders")
@CompoundIndex(name = "execution_status_queue_idx", def = "{'status': 1, 'queuedAt': 1}")
public class ExecutionWorkOrder {

    @Id
    private String id;

    @Indexed
    private Long orderId;

    @Indexed
    private ExecutionStatus status;

    private String diagnosis;
    private boolean paymentApproved;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant completedAt;
    private List<RepairRecord> repairs = new ArrayList<>();
    private Set<String> processedEventIds = new HashSet<>();
    private List<ExecutionEvent> pendingEvents = new ArrayList<>();

    public ExecutionWorkOrder() {
    }

    public ExecutionWorkOrder(Long orderId, Instant now) {
        this.id = orderId.toString();
        this.orderId = orderId;
        this.status = ExecutionStatus.WAITING_FOR_DIAGNOSIS;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public boolean isPaymentApproved() {
        return paymentApproved;
    }

    public void setPaymentApproved(boolean paymentApproved) {
        this.paymentApproved = paymentApproved;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(Instant queuedAt) {
        this.queuedAt = queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<RepairRecord> getRepairs() {
        return repairs;
    }

    public Set<String> getProcessedEventIds() {
        return processedEventIds;
    }

    public List<ExecutionEvent> getPendingEvents() {
        return pendingEvents;
    }

    public void addPendingEvent(ExecutionEvent event) {
        pendingEvents.add(event);
    }
}
