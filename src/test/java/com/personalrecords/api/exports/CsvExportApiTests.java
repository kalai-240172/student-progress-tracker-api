package com.personalrecords.api.exports;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.personalrecords.api.academic.AcademicSubject;
import com.personalrecords.api.academic.AcademicSubjectRepository;
import com.personalrecords.api.academic.AcademicYear;
import com.personalrecords.api.academic.AcademicYearRepository;
import com.personalrecords.api.academic.OfficialGrade;
import com.personalrecords.api.academic.Semester;
import com.personalrecords.api.academic.SemesterRepository;
import com.personalrecords.api.goals.Goal;
import com.personalrecords.api.goals.GoalCategory;
import com.personalrecords.api.goals.GoalRepository;
import com.personalrecords.api.goals.GoalStatus;
import com.personalrecords.api.goals.ProgressLog;
import com.personalrecords.api.goals.ProgressLogRepository;
import com.personalrecords.api.tasks.DailyTask;
import com.personalrecords.api.tasks.DailyTaskRepository;
import com.personalrecords.api.tasks.DailyTaskStatus;
import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CsvExportApiTests {

    private static final String PASSWORD = "StrongPassword123!";
    private static final LocalDate START = LocalDate.of(2026, 4, 10);
    private static final LocalDate END = LocalDate.of(2026, 4, 12);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private ProgressLogRepository logRepository;
    @Autowired private DailyTaskRepository taskRepository;
    @Autowired private AcademicYearRepository yearRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private AcademicSubjectRepository subjectRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    private User owner;
    private User other;
    private final List<Long> goalIds = new ArrayList<>();
    private final List<Long> logIds = new ArrayList<>();
    private final List<Long> taskIds = new ArrayList<>();
    private final List<Long> yearIds = new ArrayList<>();
    private final List<Long> semesterIds = new ArrayList<>();
    private final List<Long> subjectIds = new ArrayList<>();

    @BeforeEach
    void createUsers() {
        String suffix = UUID.randomUUID().toString();
        owner = userRepository.saveAndFlush(
                new User("Export Owner", "export-owner-" + suffix + "@example.com",
                        passwordEncoder.encode(PASSWORD)));
        other = userRepository.saveAndFlush(
                new User("Export Other", "export-other-" + suffix + "@example.com",
                        passwordEncoder.encode(PASSWORD)));
    }

    @AfterEach
    void removeTestData() {
        logRepository.deleteAllById(logIds);
        taskRepository.deleteAllById(taskIds);
        goalRepository.deleteAllById(goalIds);
        subjectRepository.deleteAllById(subjectIds);
        semesterRepository.deleteAllById(semesterIds);
        yearRepository.deleteAllById(yearIds);
        userRepository.delete(owner);
        userRepository.delete(other);
    }

    @Test
    void exportsInclusiveStudyHistoryWithSafeCsvAndOnlyOwnedRows() throws Exception {
        Goal goal = saveGoal("Databases", owner);
        saveLog(goal, START.minusDays(1), 15, "Outside range", null);
        saveLog(goal, START, 20, "=SUM(1,2), \"safe\"\ncontinued", null);
        saveLog(goal, END, 30, "Inclusive end", "Notes");
        Goal otherGoal = saveGoal("Private", other);
        saveLog(otherGoal, START, 10, "Other user's log", null);

        MvcResult result = stream(get("/api/exports/study-history")
                .param("startDate", START.toString())
                .param("endDate", END.toString())
                .with(httpBasic(owner.getEmail(), PASSWORD)));
        String csv = csv(result);
        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("\uFEFF\"logId\",\"goalId\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"'=SUM(1,2), \"\"safe\"\"\ncontinued\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"Inclusive end\""));
        assertThat(csv, not(containsString("Outside range")));
        assertThat(csv, not(containsString("Other user's log")));
        assertThat(result.getResponse().getContentType(), containsString("text/csv"));
        assertThat(
                result.getResponse().getHeader("Content-Disposition"), containsString("study-history.csv"));
    }

    @Test
    void exportsInclusiveTasksAndLeavesMissingActualDurationBlank() throws Exception {
        DailyTask startTask = task("Start task", START, owner);
        startTask.updateStatus(DailyTaskStatus.COMPLETED);
        startTask.updateActualDuration(45);
        saveTask(startTask);
        saveTask(task("End task", END, owner));
        saveTask(task("Outside task", END.plusDays(1), owner));
        saveTask(task("Private task", START, other));

        MvcResult result = stream(get("/api/exports/tasks")
                .param("startDate", START.toString())
                .param("endDate", END.toString())
                .with(httpBasic(owner.getEmail(), PASSWORD)));
        String csv = csv(result);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"Start task\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"COMPLETED\",\"60\",\"45\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"End task\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"PENDING\",\"60\",\"\""));
        assertThat(csv, not(containsString("Outside task")));
        assertThat(csv, not(containsString("Private task")));
    }

    @Test
    void academicExportAppliesOwnerScopedYearAndSemesterFiltersAndOfficialGradeValues() throws Exception {
        AcademicYear year = saveYear("2025-2026", owner);
        Semester semester = saveSemester("Semester 2", year);
        saveSubject(new AcademicSubject(
                "Algorithms", "CS204", 4, new BigDecimal("100"), new BigDecimal("91.50"),
                OfficialGrade.A_PLUS, semester));
        saveSubject(new AcademicSubject(
                "Not graded", "CS205", 3, new BigDecimal("100"), null, null, semester));
        AcademicYear otherYear = saveYear("Other year", other);
        Semester otherSemester = saveSemester("Other semester", otherYear);
        saveSubject(new AcademicSubject(
                "Private subject", "PRV1", 3, new BigDecimal("100"), null, null, otherSemester));

        MvcResult result = stream(get("/api/exports/academic")
                .param("academicYearId", year.getId().toString())
                .param("semesterId", semester.getId().toString())
                .with(httpBasic(owner.getEmail(), PASSWORD)));
        String csv = csv(result);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"2025-2026\",\"Semester 2\""));
        org.junit.jupiter.api.Assertions.assertTrue(
                csv.contains("\"Algorithms\",\"CS204\",\"4\",\"100.00\",\"91.50\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"91.50\",\"A+\",\"9\""));
        org.junit.jupiter.api.Assertions.assertTrue(
                csv.contains("\"Not graded\",\"CS205\",\"3\",\"100.00\",\"\",\"\",\"\",\"\""));
        assertThat(csv, not(containsString("Private subject")));
    }

    @Test
    void returnsHeaderOnlyForEmptyExportAndRejectsBadFiltersAndUnauthenticatedRequests() throws Exception {
        MvcResult empty = stream(get("/api/exports/tasks").with(httpBasic(owner.getEmail(), PASSWORD)));
        org.junit.jupiter.api.Assertions.assertEquals(
                "\uFEFF\"taskId\",\"title\",\"description\",\"subject\",\"plannedDate\",\"status\","
                        + "\"estimatedDurationMinutes\",\"actualDurationMinutes\",\"createdAt\",\"updatedAt\"\r\n",
                csv(empty));

        mockMvc.perform(get("/api/exports/tasks")
                        .param("startDate", START.toString())
                        .with(httpBasic(owner.getEmail(), PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateRange").exists());
        mockMvc.perform(get("/api/exports/study-history")
                        .param("startDate", END.toString())
                        .param("endDate", START.toString())
                        .with(httpBasic(owner.getEmail(), PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists());
        mockMvc.perform(get("/api/exports/tasks"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/exports/academic"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void academicExportRejectsAnotherUsersYearAndInconsistentSemesterFilter() throws Exception {
        AcademicYear otherYear = saveYear("Private year", other);
        AcademicYear year = saveYear("Owner year", owner);
        Semester semester = saveSemester("Semester", year);

        mockMvc.perform(get("/api/exports/academic")
                        .param("academicYearId", otherYear.getId().toString())
                        .with(httpBasic(owner.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/exports/academic")
                        .param("academicYearId", otherYear.getId().toString())
                        .param("semesterId", semester.getId().toString())
                        .with(httpBasic(owner.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound());
    }

    private DailyTask task(String title, LocalDate plannedDate, User user) {
        return new DailyTask(title, null, "Computer Science", plannedDate, 60, user);
    }

    private Goal saveGoal(String title, User user) {
        Goal saved = goalRepository.saveAndFlush(new Goal(
                title, null, GoalCategory.STUDY, null, 60, Set.of(), GoalStatus.IN_PROGRESS, user));
        goalIds.add(saved.getId());
        return saved;
    }

    private void saveLog(Goal logGoal, LocalDate date, int minutes, String summary, String notes) {
        logIds.add(logRepository.saveAndFlush(new ProgressLog(date, minutes, summary, notes, logGoal)).getId());
    }

    private void saveTask(DailyTask task) {
        taskIds.add(taskRepository.saveAndFlush(task).getId());
    }

    private AcademicYear saveYear(String label, User user) {
        AcademicYear saved = yearRepository.saveAndFlush(new AcademicYear(label, START, END, user));
        yearIds.add(saved.getId());
        return saved;
    }

    private Semester saveSemester(String label, AcademicYear parent) {
        Semester saved = semesterRepository.saveAndFlush(new Semester(label, START, END, parent));
        semesterIds.add(saved.getId());
        return saved;
    }

    private void saveSubject(AcademicSubject subject) {
        subjectIds.add(subjectRepository.saveAndFlush(subject).getId());
    }

    private MvcResult stream(org.springframework.test.web.servlet.RequestBuilder builder) throws Exception {
        MvcResult started = mockMvc.perform(builder)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();
    }

    private String csv(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
