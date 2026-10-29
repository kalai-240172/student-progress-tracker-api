package com.personalrecords.api.goals;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record GoalResponse(
        Long id,
        String title,
        String description,
        GoalCategory category,
        LocalDate targetDate,
        Integer targetDurationMinutes,
        List<DayOfWeek> preferredWeekdays,
        GoalStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static GoalResponse from(Goal goal) {
        return new GoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getCategory(),
                goal.getTargetDate(),
                goal.getTargetDurationMinutes(),
                goal.getPreferredWeekdays().stream().sorted().toList(),
                goal.getStatus(),
                goal.getCreatedAt(),
                goal.getUpdatedAt());
    }
}
