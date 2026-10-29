package com.personalrecords.api.tasks;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DailyTaskActualDurationRequest(
        @NotNull(message = "Actual duration is required")
        @PositiveOrZero(message = "Actual duration must not be negative")
        Integer actualDurationMinutes) {
}
