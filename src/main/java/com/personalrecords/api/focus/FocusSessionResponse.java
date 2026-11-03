package com.personalrecords.api.focus;

import java.time.Instant;
import java.time.LocalDateTime;

public record FocusSessionResponse(
        Long id,
        String subjectName,
        Long subjectId,
        String subjectCode,
        Long progressGoalId,
        String goalTitle,
        Integer durationMinutes,
        Instant startedAt,
        Instant completedAt,
        LocalDateTime createdAt) {

    public static FocusSessionResponse from(FocusSession session) {
        return new FocusSessionResponse(
                session.getId(),
                session.getSubjectName(),
                session.getSubjectId(),
                session.getSubjectCode(),
                session.getProgressGoalId(),
                session.getGoalTitle(),
                session.getDurationMinutes(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getCreatedAt());
    }
}
