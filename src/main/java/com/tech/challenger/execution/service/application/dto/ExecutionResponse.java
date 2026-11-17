package com.tech.challenger.execution.service.application.dto;

import com.tech.challenger.execution.service.domain.ExecutionStatus;
import com.tech.challenger.execution.service.domain.RepairRecord;
import java.time.Instant;
import java.util.List;

public record ExecutionResponse(
        Long orderId,
        ExecutionStatus status,
        String diagnosis,
        boolean paymentApproved,
        boolean refundRequired,
        Instant createdAt,
        Instant updatedAt,
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt,
        List<RepairRecord> repairs,
        Integer queuePosition) {
}
