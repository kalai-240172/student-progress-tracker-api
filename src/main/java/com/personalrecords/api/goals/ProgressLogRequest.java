package com.personalrecords.api.goals;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProgressLogRequest(
        @NotNull(message = "Activity date is required")
        LocalDate activityDate,
        @NotNull(message = "Time spent is required")
        @Positive(message = "Time spent must be greater than zero")
        Integer timeSpentMinutes,
        @NotBlank(message = "Summary is required")
        @Size(max = 2000, message = "Summary must be at most 2000 characters")
        String summary,
        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes) {
}
