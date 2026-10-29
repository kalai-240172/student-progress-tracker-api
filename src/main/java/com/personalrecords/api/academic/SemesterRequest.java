package com.personalrecords.api.academic;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SemesterRequest(
        @NotBlank(message = "Semester label is required")
        @Size(max = 80, message = "Semester label must be at most 80 characters")
        String label,
        LocalDate startDate,
        LocalDate endDate) {

    @AssertTrue(message = "End date must not be earlier than start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
