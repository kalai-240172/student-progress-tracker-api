package com.personalrecords.api.academic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public record AcademicSubjectResponse(
        Long id,
        Long semesterId,
        String name,
        String code,
        Integer credits,
        BigDecimal maximumMarks,
        BigDecimal obtainedMarks,
        BigDecimal percentage,
        String officialGrade,
        Integer gradePoint,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AcademicSubjectResponse from(AcademicSubject subject) {
        OfficialGrade grade = subject.getOfficialGrade();
        BigDecimal percentage = subject.getObtainedMarks() == null
                ? null
                : subject.getObtainedMarks()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(subject.getMaximumMarks(), 2, RoundingMode.HALF_UP);
        return new AcademicSubjectResponse(
                subject.getId(),
                subject.getSemester().getId(),
                subject.getName(),
                subject.getCode(),
                subject.getCredits(),
                subject.getMaximumMarks(),
                subject.getObtainedMarks(),
                percentage,
                grade == null ? null : grade.getLabel(),
                grade == null ? null : grade.getGradePoint(),
                subject.getCreatedAt(),
                subject.getUpdatedAt());
    }
}
