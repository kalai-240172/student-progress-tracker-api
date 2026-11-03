package com.personalrecords.api.focus;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FocusSessionRequest(
        @NotNull @Positive Long subjectId,
        Long goalId,
        @NotNull @Positive Integer durationMinutes,
        @NotNull Instant startedAt,
        @NotNull Instant completedAt,
        @Size(min = 36, max = 36) String requestKey) {
}
