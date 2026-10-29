package com.personalrecords.api.tasks;

import jakarta.validation.constraints.NotNull;

public record DailyTaskStatusRequest(
        @NotNull(message = "Status is required")
        DailyTaskStatus status) {
}
