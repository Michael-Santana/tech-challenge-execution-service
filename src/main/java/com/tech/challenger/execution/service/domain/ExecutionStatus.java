package com.tech.challenger.execution.service.domain;

public enum ExecutionStatus {
    WAITING_FOR_DIAGNOSIS,
    IN_DIAGNOSIS,
    WAITING_FOR_APPROVAL,
    QUEUED_FOR_REPAIR,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
