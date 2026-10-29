package com.personalrecords.api.tasks;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.personalrecords.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "daily_tasks", indexes = {
        @Index(name = "idx_daily_tasks_user_date", columnList = "user_id, planned_date")
})
public class DailyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String title;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String subject;

    @NotNull
    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer estimatedDurationMinutes;

    @PositiveOrZero
    private Integer actualDurationMinutes;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DailyTaskStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected DailyTask() {
    }

    public DailyTask(
            String title,
            String description,
            String subject,
            LocalDate plannedDate,
            Integer estimatedDurationMinutes,
            User user) {
        this.title = title;
        this.description = description;
        this.subject = subject;
        this.plannedDate = plannedDate;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.status = DailyTaskStatus.PENDING;
        this.user = user;
    }

    public void update(
            String title,
            String description,
            String subject,
            LocalDate plannedDate,
            Integer estimatedDurationMinutes) {
        this.title = title;
        this.description = description;
        this.subject = subject;
        this.plannedDate = plannedDate;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }

    public void updateStatus(DailyTaskStatus status) {
        this.status = status;
    }

    public void updateActualDuration(Integer actualDurationMinutes) {
        this.actualDurationMinutes = actualDurationMinutes;
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

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public Integer getActualDurationMinutes() {
        return actualDurationMinutes;
    }

    public DailyTaskStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
