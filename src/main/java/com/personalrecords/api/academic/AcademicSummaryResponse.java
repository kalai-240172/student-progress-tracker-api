package com.personalrecords.api.academic;

import java.math.BigDecimal;
import java.util.List;

public record AcademicSummaryResponse(
        int totalSubjects,
        int eligibleSubjects,
        int eligibleCredits,
        BigDecimal weightedGradePoints,
        BigDecimal cgpa,
        List<AcademicYearSummaryResponse> academicYears) {
}
