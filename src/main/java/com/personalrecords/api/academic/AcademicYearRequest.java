package com.personalrecords.api.academic;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcademicYearRequest(
        @NotBlank(message = "Academic year label is required")
        @Size(max = 40, message = "Academic year label must be at most 40 characters")
        String label,
        LocalDate startDate,
        LocalDate endDate) {

    @AssertTrue(message = "End date must not be earlier than start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
