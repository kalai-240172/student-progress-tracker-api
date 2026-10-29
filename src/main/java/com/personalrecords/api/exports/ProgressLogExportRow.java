package com.personalrecords.api.exports;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ProgressLogExportRow {
    Long getId();
    Long getGoalId();
    String getGoalTitle();
    LocalDate getActivityDate();
    Integer getTimeSpentMinutes();
    String getSummary();
    String getNotes();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
