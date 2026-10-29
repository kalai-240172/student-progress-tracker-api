package com.personalrecords.api.tasks;

import java.time.LocalDate;

public record DailyTaskSummaryResponse(
        LocalDate date,
        long taskCount,
        long completedCount,
        long pendingOrInProgressCount,
        long plannedMinutes,
        long actualMinutes) {
}
