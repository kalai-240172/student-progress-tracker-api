package com.personalrecords.api.academic;

import java.math.BigDecimal;
import java.util.List;

public record SemesterSummaryResponse(
        SemesterResponse semester,
        int totalSubjects,
        int eligibleSubjects,
        int eligibleCredits,
        BigDecimal weightedGradePoints,
        BigDecimal gpa,
        List<AcademicSubjectResponse> subjects) {
}
