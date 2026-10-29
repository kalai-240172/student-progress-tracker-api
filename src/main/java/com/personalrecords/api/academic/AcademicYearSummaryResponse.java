package com.personalrecords.api.academic;

import java.math.BigDecimal;
import java.util.List;

public record AcademicYearSummaryResponse(
        AcademicYearResponse academicYear,
        int totalSubjects,
        int eligibleSubjects,
        int eligibleCredits,
        BigDecimal weightedGradePoints,
        BigDecimal cgpa,
        List<SemesterSummaryResponse> semesters) {
}
