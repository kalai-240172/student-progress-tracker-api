package com.personalrecords.api.academic;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AcademicYearResponse(
        Long id,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AcademicYearResponse from(AcademicYear year) {
        return new AcademicYearResponse(
                year.getId(), year.getLabel(), year.getStartDate(), year.getEndDate(),
                year.getCreatedAt(), year.getUpdatedAt());
    }
}
