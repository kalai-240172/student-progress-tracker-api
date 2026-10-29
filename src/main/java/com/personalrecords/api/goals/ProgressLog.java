package com.personalrecords.api.goals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "progress_logs", indexes = {
        @Index(name = "idx_progress_logs_goal_activity_date", columnList = "goal_id, activity_date")
})
public class ProgressLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer timeSpentMinutes;

    @NotBlank
    @Size(max = 2000)
    @Column(nullable = false, length = 2000)
    private String summary;

    @Size(max = 2000)
    @Column(length = 2000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    protected ProgressLog() {
    }

    public ProgressLog(
            LocalDate activityDate,
            Integer timeSpentMinutes,
            String summary,
            String notes,
            Goal goal) {
        this.activityDate = activityDate;
        this.timeSpentMinutes = timeSpentMinutes;
        this.summary = summary;
        this.notes = notes;
        this.goal = goal;
    }

    public void update(
            LocalDate activityDate,
            Integer timeSpentMinutes,
            String summary,
            String notes) {
        this.activityDate = activityDate;
        this.timeSpentMinutes = timeSpentMinutes;
        this.summary = summary;
        this.notes = notes;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public Integer getTimeSpentMinutes() {
        return timeSpentMinutes;
    }

    public String getSummary() {
        return summary;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
