package com.personalrecords.api.academic;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SemesterResponse(
        Long id,
        Long academicYearId,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static SemesterResponse from(Semester semester) {
        return new SemesterResponse(
                semester.getId(), semester.getAcademicYear().getId(), semester.getLabel(),
                semester.getStartDate(), semester.getEndDate(),
                semester.getCreatedAt(), semester.getUpdatedAt());
    }
}
