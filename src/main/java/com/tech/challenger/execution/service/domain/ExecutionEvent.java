package com.tech.challenger.execution.service.domain;

import java.time.Instant;

public class ExecutionEvent {

    private String eventId;
    private String eventType;
    private Long orderId;
    private String status;
    private String description;
    private Instant occurredAt;
    private boolean published;

    public ExecutionEvent() {
    }

    public ExecutionEvent(String eventId, String eventType, Long orderId, String status,
                          String description, Instant occurredAt, boolean published) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.orderId = orderId;
        this.status = status;
        this.description = description;
        this.occurredAt = occurredAt;
        this.published = published;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public boolean isPublished() {
        return published;
    }

    public void markPublished() {
        published = true;
    }
}
