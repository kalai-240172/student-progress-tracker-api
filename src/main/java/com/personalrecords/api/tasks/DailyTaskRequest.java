package com.personalrecords.api.tasks;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DailyTaskRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,
        @NotBlank(message = "Subject is required")
        @Size(max = 100, message = "Subject must be at most 100 characters")
        String subject,
        @NotNull(message = "Planned date is required")
        LocalDate plannedDate,
        @NotNull(message = "Estimated duration is required")
        @Positive(message = "Estimated duration must be greater than zero")
        Integer estimatedDurationMinutes) {
}
