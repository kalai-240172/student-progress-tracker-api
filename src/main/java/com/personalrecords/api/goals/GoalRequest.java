package com.personalrecords.api.goals;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GoalRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,
        @NotNull(message = "Category is required")
        GoalCategory category,
        LocalDate targetDate,
        @NotNull(message = "Target duration is required")
        @Positive(message = "Target duration must be greater than zero")
        Integer targetDurationMinutes,
        @Size(max = 7, message = "At most seven preferred weekdays may be selected")
        Set<DayOfWeek> preferredWeekdays,
        @NotNull(message = "Status is required")
        GoalStatus status) {
}
