package com.personalrecords.api.academic;

import java.math.BigDecimal;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "academic_subjects",
        indexes = @Index(name = "idx_academic_subjects_semester_id", columnList = "semester_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_academic_subject_semester_code", columnNames = {"semester_id", "subject_code"}))
public class AcademicSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "subject_code", length = 30)
    private String code;

    @Column(nullable = false)
    private Integer credits;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal maximumMarks;

    @Column(precision = 10, scale = 2)
    private BigDecimal obtainedMarks;

    @Column(length = 10)
    private String officialGrade;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    protected AcademicSubject() {
    }

    public AcademicSubject(
            String name,
            String code,
            Integer credits,
            BigDecimal maximumMarks,
            BigDecimal obtainedMarks,
            OfficialGrade officialGrade,
            Semester semester) {
        this.name = name;
        this.code = code;
        this.credits = credits;
        this.maximumMarks = maximumMarks;
        this.obtainedMarks = obtainedMarks;
        this.officialGrade = officialGrade == null ? null : officialGrade.name();
        this.semester = semester;
    }

    public void update(
            String name,
            String code,
            Integer credits,
            BigDecimal maximumMarks,
            BigDecimal obtainedMarks,
            OfficialGrade officialGrade) {
        this.name = name;
        this.code = code;
        this.credits = credits;
        this.maximumMarks = maximumMarks;
        this.obtainedMarks = obtainedMarks;
        this.officialGrade = officialGrade == null ? null : officialGrade.name();
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

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public Integer getCredits() { return credits; }
    public BigDecimal getMaximumMarks() { return maximumMarks; }
    public BigDecimal getObtainedMarks() { return obtainedMarks; }
    public OfficialGrade getOfficialGrade() {
        return officialGrade == null ? null : OfficialGrade.valueOf(officialGrade);
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Semester getSemester() { return semester; }
}
