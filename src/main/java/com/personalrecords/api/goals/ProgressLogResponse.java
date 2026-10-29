package com.personalrecords.api.goals;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProgressLogResponse(
        Long id,
        Long goalId,
        LocalDate activityDate,
        Integer timeSpentMinutes,
        String summary,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProgressLogResponse from(ProgressLog log, Long goalId) {
        return new ProgressLogResponse(
                log.getId(),
                goalId,
                log.getActivityDate(),
                log.getTimeSpentMinutes(),
                log.getSummary(),
                log.getNotes(),
                log.getCreatedAt(),
                log.getUpdatedAt());
    }
}
