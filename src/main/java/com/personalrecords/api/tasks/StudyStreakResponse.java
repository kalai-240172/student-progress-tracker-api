package com.personalrecords.api.tasks;

import java.time.LocalDate;

public record StudyStreakResponse(
        LocalDate asOfDate,
        long currentStreakDays,
        long longestStreakDays) {
}
