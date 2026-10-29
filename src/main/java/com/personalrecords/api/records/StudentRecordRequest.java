package com.personalrecords.api.records;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentRecordRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,
        @NotNull(message = "Category is required")
        RecordCategory category,
        @Size(max = 200, message = "Institution must be at most 200 characters")
        String institution,
        LocalDate startDate,
        LocalDate endDate,
        @NotNull(message = "Status is required")
        RecordStatus status) {

    @AssertTrue(message = "End date must not be earlier than start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
