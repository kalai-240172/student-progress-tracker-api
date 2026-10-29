package com.personalrecords.api.exports;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.personalrecords.api.academic.AcademicResourceNotFoundException;
import com.personalrecords.api.academic.AcademicService;
import com.personalrecords.api.academic.AcademicSubject;
import com.personalrecords.api.academic.AcademicSubjectRepository;
import com.personalrecords.api.academic.AcademicSubjectResponse;
import com.personalrecords.api.academic.AcademicYearRepository;
import com.personalrecords.api.academic.AcademicYearSummaryResponse;
import com.personalrecords.api.academic.Semester;
import com.personalrecords.api.academic.SemesterRepository;
import com.personalrecords.api.academic.SemesterSummaryResponse;
import com.personalrecords.api.goals.ProgressLogRepository;
import com.personalrecords.api.tasks.DailyTask;
import com.personalrecords.api.tasks.DailyTaskRepository;
import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class CsvExportService {

    private static final String[] STUDY_HISTORY_HEADERS = {
            "logId", "goalId", "goalTitle", "activityDate", "timeSpentMinutes",
            "summary", "notes", "createdAt", "updatedAt"
    };
    private static final String[] TASK_HEADERS = {
            "taskId", "title", "description", "subject", "plannedDate", "status",
            "estimatedDurationMinutes", "actualDurationMinutes", "createdAt", "updatedAt"
    };
    private static final String[] ACADEMIC_HEADERS = {
            "academicYear", "semester", "subjectId", "subjectName", "subjectCode", "credits",
            "maximumMarks", "obtainedMarks", "percentage", "officialLetterGrade", "gradePoint",
            "semesterEligibleCredits", "semesterGpa", "academicYearEligibleCredits", "academicYearCgpa"
    };

    private final ProgressLogRepository logRepository;
    private final DailyTaskRepository taskRepository;
    private final AcademicSubjectRepository subjectRepository;
    private final AcademicYearRepository yearRepository;
    private final SemesterRepository semesterRepository;
    private final UserRepository userRepository;
    private final AcademicService academicService;
    private final TransactionTemplate readTransaction;

    public CsvExportService(
            ProgressLogRepository logRepository,
            DailyTaskRepository taskRepository,
            AcademicSubjectRepository subjectRepository,
            AcademicYearRepository yearRepository,
            SemesterRepository semesterRepository,
            UserRepository userRepository,
            AcademicService academicService,
            PlatformTransactionManager transactionManager) {
        this.logRepository = logRepository;
        this.taskRepository = taskRepository;
        this.subjectRepository = subjectRepository;
        this.yearRepository = yearRepository;
        this.semesterRepository = semesterRepository;
        this.userRepository = userRepository;
        this.academicService = academicService;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public StreamingResponseBody exportStudyHistory(
            String authenticatedEmail, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        User user = findUser(authenticatedEmail);
        return output -> runStreamingTransaction(output, writer -> {
            writer.writeRow(CsvWriter.headers(STUDY_HISTORY_HEADERS));
            try (Stream<ProgressLogExportRow> rows =
                         logRepository.streamForExport(user.getId(), startDate, endDate)) {
                rows.forEach(row -> writeUnchecked(writer,
                        CsvWriter.raw(row.getId()),
                        CsvWriter.raw(row.getGoalId()),
                        CsvWriter.text(row.getGoalTitle()),
                        CsvWriter.raw(row.getActivityDate()),
                        CsvWriter.raw(row.getTimeSpentMinutes()),
                        CsvWriter.text(row.getSummary()),
                        CsvWriter.text(row.getNotes()),
                        CsvWriter.raw(row.getCreatedAt()),
                        CsvWriter.raw(row.getUpdatedAt())));
            }
        });
    }

    public StreamingResponseBody exportTasks(
            String authenticatedEmail, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        User user = findUser(authenticatedEmail);
        return output -> runStreamingTransaction(output, writer -> {
            writer.writeRow(CsvWriter.headers(TASK_HEADERS));
            try (Stream<DailyTask> rows =
                         taskRepository.streamForExport(user.getId(), startDate, endDate)) {
                rows.forEach(task -> writeUnchecked(writer,
                        CsvWriter.raw(task.getId()),
                        CsvWriter.text(task.getTitle()),
                        CsvWriter.text(task.getDescription()),
                        CsvWriter.text(task.getSubject()),
                        CsvWriter.raw(task.getPlannedDate()),
                        CsvWriter.raw(task.getStatus()),
                        CsvWriter.raw(task.getEstimatedDurationMinutes()),
                        CsvWriter.raw(task.getActualDurationMinutes()),
                        CsvWriter.raw(task.getCreatedAt()),
                        CsvWriter.raw(task.getUpdatedAt())));
            }
        });
    }

    public StreamingResponseBody exportAcademic(
            String authenticatedEmail, Long academicYearId, Long semesterId) {
        User user = findUser(authenticatedEmail);
        validateAcademicFilter(user.getId(), academicYearId, semesterId);
        return output -> runStreamingTransaction(output, writer -> {
            writer.writeRow(CsvWriter.headers(ACADEMIC_HEADERS));
            Stream<AcademicSubject> subjects = academicSubjectStream(user.getId(), academicYearId, semesterId);
            Map<Long, SemesterSummaryResponse> semesterSummaries = new HashMap<>();
            Map<Long, AcademicYearSummaryResponse> yearSummaries = new HashMap<>();
            try (subjects) {
                subjects.forEach(subject -> {
                    Semester semester = subject.getSemester();
                    Long yearId = semester.getAcademicYear().getId();
                    SemesterSummaryResponse semesterSummary = semesterSummaries.computeIfAbsent(
                            semester.getId(), id -> academicService.summarizeSemester(authenticatedEmail, id));
                    AcademicYearSummaryResponse yearSummary = yearSummaries.computeIfAbsent(
                            yearId, id -> academicService.summarizeYear(authenticatedEmail, id));
                    AcademicSubjectResponse response = AcademicSubjectResponse.from(subject);
                    writeUnchecked(writer,
                            CsvWriter.text(yearSummary.academicYear().label()),
                            CsvWriter.text(semester.getLabel()),
                            CsvWriter.raw(response.id()),
                            CsvWriter.text(response.name()),
                            CsvWriter.text(response.code()),
                            CsvWriter.raw(response.credits()),
                            CsvWriter.raw(response.maximumMarks()),
                            CsvWriter.raw(response.obtainedMarks()),
                            CsvWriter.raw(response.percentage()),
                            CsvWriter.raw(response.officialGrade()),
                            CsvWriter.raw(response.gradePoint()),
                            CsvWriter.raw(semesterSummary.eligibleCredits()),
                            CsvWriter.raw(semesterSummary.gpa()),
                            CsvWriter.raw(yearSummary.eligibleCredits()),
                            CsvWriter.raw(yearSummary.cgpa()));
                });
            }
        });
    }

    private Stream<AcademicSubject> academicSubjectStream(
            Long userId, Long academicYearId, Long semesterId) {
        if (semesterId != null) {
            return subjectRepository
                    .streamAllBySemester_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(semesterId, userId);
        }
        if (academicYearId != null) {
            return subjectRepository
                    .streamAllBySemester_AcademicYear_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(
                            academicYearId, userId);
        }
        return subjectRepository.streamAllBySemester_AcademicYear_User_IdOrderByIdAsc(userId);
    }

    private void validateAcademicFilter(Long userId, Long academicYearId, Long semesterId) {
        if (academicYearId != null && academicYearId <= 0) {
            throw new ExportFilterException("academicYearId", "Academic year ID must be positive");
        }
        if (semesterId != null && semesterId <= 0) {
            throw new ExportFilterException("semesterId", "Semester ID must be positive");
        }
        if (academicYearId != null && !yearRepository.existsByIdAndUser_Id(academicYearId, userId)) {
            throw new AcademicResourceNotFoundException();
        }
        if (semesterId != null) {
            Semester semester = semesterRepository.findByIdAndAcademicYear_User_Id(semesterId, userId)
                    .orElseThrow(AcademicResourceNotFoundException::new);
            if (academicYearId != null
                    && !semester.getAcademicYear().getId().equals(academicYearId)) {
                throw new AcademicResourceNotFoundException();
            }
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if ((startDate == null) != (endDate == null)) {
            throw new ExportFilterException("dateRange", "Provide both startDate and endDate");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new ExportFilterException("startDate", "startDate must be on or before endDate");
        }
    }

    private User findUser(String email) {
        return userRepository.findByNormalizedEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user was not found"));
    }

    private void runStreamingTransaction(
            java.io.OutputStream output, CsvExport writerAction) throws IOException {
        try {
            readTransaction.executeWithoutResult(status -> {
                try {
                    CsvWriter writer = new CsvWriter(output);
                    writerAction.write(writer);
                    writer.flush();
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private void writeUnchecked(CsvWriter writer, CsvWriter.CsvField... fields) {
        try {
            writer.writeRow(fields);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @FunctionalInterface
    private interface CsvExport {
        void write(CsvWriter writer) throws IOException;
    }
}
