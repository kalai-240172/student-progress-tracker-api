package com.personalrecords.api.goals;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.personalrecords.api.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "goals", indexes = {
        @Index(name = "idx_goals_user_id", columnList = "user_id")
})
public class Goal {

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalCategory category;

    private LocalDate targetDate;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer targetDurationMinutes;

    @ElementCollection
    @CollectionTable(
            name = "goal_preferred_weekdays",
            joinColumns = @JoinColumn(name = "goal_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_goal_preferred_weekday", columnNames = {"goal_id", "weekday"}))
    @Enumerated(EnumType.STRING)
    @Column(name = "weekday", nullable = false, length = 9)
    private Set<DayOfWeek> preferredWeekdays = new HashSet<>();

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @OneToMany(mappedBy = "goal", cascade = CascadeType.REMOVE)
    private List<ProgressLog> progressLogs = new ArrayList<>();

    protected Goal() {
    }

    public Goal(
            String title,
            String description,
            GoalCategory category,
            LocalDate targetDate,
            Integer targetDurationMinutes,
            Set<DayOfWeek> preferredWeekdays,
            GoalStatus status,
            User user) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.targetDate = targetDate;
        this.targetDurationMinutes = targetDurationMinutes;
        this.preferredWeekdays = new HashSet<>(preferredWeekdays);
        this.status = status;
        this.user = user;
    }

    public void update(
            String title,
            String description,
            GoalCategory category,
            LocalDate targetDate,
            Integer targetDurationMinutes,
            Set<DayOfWeek> preferredWeekdays,
            GoalStatus status) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.targetDate = targetDate;
        this.targetDurationMinutes = targetDurationMinutes;
        this.preferredWeekdays = new HashSet<>(preferredWeekdays);
        this.status = status;
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

    public GoalCategory getCategory() {
        return category;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public Integer getTargetDurationMinutes() {
        return targetDurationMinutes;
    }

    public Long getUserId() {
        return user.getId();
    }

    public Set<DayOfWeek> getPreferredWeekdays() {
        return preferredWeekdays;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
