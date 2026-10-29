package com.personalrecords.api.tasks;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyTaskResponse(
        Long id,
        String title,
        String description,
        String subject,
        LocalDate plannedDate,
        Integer estimatedDurationMinutes,
        Integer actualDurationMinutes,
        DailyTaskStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DailyTaskResponse from(DailyTask task) {
        return new DailyTaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getSubject(),
                task.getPlannedDate(),
                task.getEstimatedDurationMinutes(),
                task.getActualDurationMinutes(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
