package com.personalrecords.api.focus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.personalrecords.api.user.User;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "focus_sessions",
        indexes = @Index(name = "idx_focus_sessions_user_completed_at", columnList = "user_id, completed_at"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_focus_sessions_user_request_key",
                columnNames = {"user_id", "request_key"}))
public class FocusSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_key", nullable = false, length = 36)
    private String requestKey;

    @Column(name = "subject_name", nullable = false, length = 150)
    private String subjectName;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "subject_code", length = 30)
    private String subjectCode;

    @Column(name = "progress_goal_id")
    private Long progressGoalId;

    @Column(name = "goal_title", length = 150)
    private String goalTitle;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected FocusSession() {
    }

    public FocusSession(
            String requestKey,
            String subjectName,
            Long subjectId,
            String subjectCode,
            Long progressGoalId,
            String goalTitle,
            Integer durationMinutes,
            Instant startedAt,
            Instant completedAt,
            User user) {
        this.requestKey = requestKey;
        this.subjectName = subjectName;
        this.subjectId = subjectId;
        this.subjectCode = subjectCode;
        this.progressGoalId = progressGoalId;
        this.goalTitle = goalTitle;
        this.durationMinutes = durationMinutes;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.user = user;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public Long getProgressGoalId() {
        return progressGoalId;
    }

    public String getGoalTitle() {
        return goalTitle;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAtInLocalDateTime() {
        return LocalDateTime.ofInstant(completedAt, ZoneId.systemDefault());
    }
}
