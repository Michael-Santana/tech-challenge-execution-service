package com.tech.challenger.execution.service.domain;

import java.time.Instant;

public record RepairRecord(String description, Instant recordedAt) {
}
