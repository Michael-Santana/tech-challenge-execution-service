package com.tech.challenger.execution.service.application.dto;

public record OsEvent(String id, Long orderId, String eventType, String newStatus, String occurredAt) {
}
