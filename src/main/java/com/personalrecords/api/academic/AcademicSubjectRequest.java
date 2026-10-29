package com.personalrecords.api.academic;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AcademicSubjectRequest(
        @NotBlank(message = "Subject name is required")
        @Size(max = 150, message = "Subject name must be at most 150 characters")
        String name,
        @Size(max = 30, message = "Subject code must be at most 30 characters")
        String code,
        @NotNull(message = "Credits are required")
        @Positive(message = "Credits must be positive")
        Integer credits,
        @NotNull(message = "Maximum marks are required")
        @DecimalMin(value = "0.00", inclusive = false, message = "Maximum marks must be positive")
        @Digits(integer = 8, fraction = 2, message = "Maximum marks may have at most two decimal places")
        BigDecimal maximumMarks,
        @DecimalMin(value = "0.00", message = "Obtained marks cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Obtained marks may have at most two decimal places")
        BigDecimal obtainedMarks,
        OfficialGrade officialGrade,
        Integer gradePoint) {

    @AssertTrue(message = "Obtained marks must not exceed maximum marks")
    public boolean isObtainedMarksWithinMaximum() {
        return obtainedMarks == null || maximumMarks == null || obtainedMarks.compareTo(maximumMarks) <= 0;
    }

    @AssertTrue(message = "Grade point is calculated from the official grade and cannot be supplied")
    public boolean isGradePointNotSupplied() {
        return gradePoint == null;
    }
}
