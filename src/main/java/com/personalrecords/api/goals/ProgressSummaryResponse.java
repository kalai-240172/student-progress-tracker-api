package com.personalrecords.api.goals;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProgressSummaryResponse(
        Long goalId,
        String goalTitle,
        Integer targetDurationMinutes,
        Long totalRecordedMinutes,
        Long remainingMinutes,
        BigDecimal progressPercentage,
        Long totalLogCount,
        Long currentWeekMinutes,
        Long currentMonthMinutes,
        LocalDate mostRecentActivityDate) {
}
