package com.tech.challenger.execution.service.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RepairRequest(@NotBlank @Size(max = 500) String description) {
}
