package com.personalrecords.api.records;

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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "student_records", indexes = {
        @Index(name = "idx_student_records_user_id", columnList = "user_id")
})
public class StudentRecord {

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
    private RecordCategory category;

    @Size(max = 200)
    @Column(length = 200)
    private String institution;

    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected StudentRecord() {
    }

    public StudentRecord(
            String title,
            String description,
            RecordCategory category,
            String institution,
            LocalDate startDate,
            LocalDate endDate,
            RecordStatus status,
            User user) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.institution = institution;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.user = user;
    }

    public void update(
            String title,
            String description,
            RecordCategory category,
            String institution,
            LocalDate startDate,
            LocalDate endDate,
            RecordStatus status) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.institution = institution;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    @AssertTrue(message = "End date must not be earlier than start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
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

    public RecordCategory getCategory() {
        return category;
    }

    public String getInstitution() {
        return institution;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
