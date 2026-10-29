package com.personalrecords.api.records;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StudentRecordResponse(
        Long id,
        String title,
        String description,
        RecordCategory category,
        String institution,
        LocalDate startDate,
        LocalDate endDate,
        RecordStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static StudentRecordResponse from(StudentRecord record) {
        return new StudentRecordResponse(
                record.getId(),
                record.getTitle(),
                record.getDescription(),
                record.getCategory(),
                record.getInstitution(),
                record.getStartDate(),
                record.getEndDate(),
                record.getStatus(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
