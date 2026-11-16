package com.tech.challenger.execution.service.application.dto;

public record BillingEvent(String eventId, String eventType, Long orderId, String budgetId, String occurredAt) {
}
