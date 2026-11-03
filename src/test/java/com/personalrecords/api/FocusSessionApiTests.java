package com.personalrecords.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import com.personalrecords.api.academic.AcademicSubject;
import com.personalrecords.api.academic.AcademicSubjectRepository;
import com.personalrecords.api.academic.AcademicYear;
import com.personalrecords.api.academic.AcademicYearRepository;
import com.personalrecords.api.academic.OfficialGrade;
import com.personalrecords.api.academic.Semester;
import com.personalrecords.api.academic.SemesterRepository;
import com.personalrecords.api.focus.FocusSessionRepository;
import com.personalrecords.api.goals.Goal;
import com.personalrecords.api.goals.GoalCategory;
import com.personalrecords.api.goals.GoalRepository;
import com.personalrecords.api.goals.GoalStatus;
import com.personalrecords.api.goals.ProgressLogRepository;
import com.personalrecords.api.records.StudentRecordRepository;
import com.personalrecords.api.tasks.DailyTaskRepository;
import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:focus-session-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FocusSessionApiTests {

    private static final String PASSWORD = "StrongPassword123!";
    private static final String OWNER_EMAIL = "focus-owner@example.com";
    private static final String OTHER_EMAIL = "focus-other@example.com";
    private static final String REQUEST_KEY = "63d3ec54-1f30-4d3e-83af-202611021915";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademicYearRepository yearRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private AcademicSubjectRepository subjectRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private ProgressLogRepository progressLogRepository;

    @Autowired
    private DailyTaskRepository taskRepository;

    @Autowired
    private FocusSessionRepository focusSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long subjectId;
    private Long goalId;

    @BeforeEach
    void setUp() {
        focusSessionRepository.deleteAll();
        progressLogRepository.deleteAll();
        taskRepository.deleteAll();
        goalRepository.deleteAll();
        subjectRepository.deleteAll();
        semesterRepository.deleteAll();
        yearRepository.deleteAll();
        userRepository.deleteAll();

        User owner = userRepository.saveAndFlush(
                new User("Focus Owner", OWNER_EMAIL, passwordEncoder.encode(PASSWORD)));
        userRepository.saveAndFlush(
                new User("Focus Other", OTHER_EMAIL, passwordEncoder.encode(PASSWORD)));
        AcademicYear year = yearRepository.saveAndFlush(
                new AcademicYear("2026-2027", null, null, owner));
        Semester semester = semesterRepository.saveAndFlush(
                new Semester("Semester 1", null, null, year));
        AcademicSubject subject = subjectRepository.saveAndFlush(new AcademicSubject(
                "Data Structures",
                "CS201",
                4,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(90),
                OfficialGrade.A_PLUS,
                semester));
        Goal goal = goalRepository.saveAndFlush(new Goal(
                "Master algorithms",
                "Practice data structures and algorithms.",
                GoalCategory.STUDY,
                null,
                900,
                Set.of(),
                GoalStatus.IN_PROGRESS,
                owner));
        subjectId = subject.getId();
        goalId = goal.getId();
    }

    @Test
    void completedFocusSessionIsSavedOnceAndFeedsTaskHistoryAndGoalProgress() throws Exception {
        String request = focusRequest(REQUEST_KEY, subjectId, goalId, 25);

        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectName").value("Data Structures"))
                .andExpect(jsonPath("$.subjectCode").value("CS201"))
                .andExpect(jsonPath("$.durationMinutes").value(25))
                .andExpect(jsonPath("$.progressGoalId").value(goalId))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectName").value("Data Structures"));

        Long ownerId = userRepository.findByNormalizedEmail(OWNER_EMAIL).orElseThrow().getId();
        org.junit.jupiter.api.Assertions.assertEquals(1, focusSessionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, taskRepository.findAllByUser_IdOrderByPlannedDateAscIdAsc(ownerId).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, progressLogRepository
                .findAllByGoal_IdAndGoal_User_IdOrderByActivityDateDescIdDesc(goalId, ownerId).size());

        mockMvc.perform(get("/api/tasks").with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].actualDurationMinutes").value(25))
                .andExpect(jsonPath("$[0].subject").value("Data Structures"));

        mockMvc.perform(get("/api/goals/{id}/progress", goalId).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecordedMinutes").value(25))
                .andExpect(jsonPath("$.totalLogCount").value(1));
    }

    @Test
    void focusSessionWritesRequireCsrfAndCannotUseAnotherUsersSubject() throws Exception {
        String request = focusRequest(REQUEST_KEY, subjectId, null, 25);

        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals(0, focusSessionRepository.count());
        org.junit.jupiter.api.Assertions.assertTrue(taskRepository.findAll().isEmpty());
    }

    @Test
    void onlyCompletedFocusDurationsInFiveMinuteStepsCanBeSaved() throws Exception {
        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(focusRequest(REQUEST_KEY, subjectId, null, 7)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists());

        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(focusRequest("9bb9715f-2226-4305-a802-202611021915", subjectId, null, 125)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists());

        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(focusRequest("3bb9715f-2226-4305-a802-202611021915", subjectId, null, 60, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.completedAt").exists());

        org.junit.jupiter.api.Assertions.assertEquals(0, focusSessionRepository.count());
        org.junit.jupiter.api.Assertions.assertTrue(taskRepository.findAll().isEmpty());
    }

    @Test
    void selectedFocusDurationIsReflectedInSessionsTasksAndGoalProgress() throws Exception {
        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(focusRequest(REQUEST_KEY, subjectId, goalId, 5, 6)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(5));

        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(focusRequest("9bb9715f-2226-4305-a802-202611021915", subjectId, goalId, 60, 61)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(60));

        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(focusRequest("3bb9715f-2226-4305-a802-202611021915", subjectId, goalId, 120, 121)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(120));

        Long ownerId = userRepository.findByNormalizedEmail(OWNER_EMAIL).orElseThrow().getId();
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of(5, 60, 120),
                taskRepository.findAllByUser_IdOrderByPlannedDateAscIdAsc(ownerId).stream()
                        .map(task -> task.getActualDurationMinutes())
                        .sorted()
                        .toList());
        org.junit.jupiter.api.Assertions.assertEquals(185, progressLogRepository
                .findAllByGoal_IdAndGoal_User_IdOrderByActivityDateDescIdDesc(goalId, ownerId).stream()
                .mapToInt(log -> log.getTimeSpentMinutes())
                .sum());
    }

    @Test
    void listingFocusSessionsIsOwnerScopedAndValidatesDateRanges() throws Exception {
        mockMvc.perform(post("/api/focus-sessions")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(focusRequest(REQUEST_KEY, subjectId, null, 25)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/focus-sessions")
                        .param("startDate", java.time.LocalDate.now().toString())
                        .param("endDate", java.time.LocalDate.now().toString())
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/focus-sessions")
                        .param("startDate", "2026-11-03")
                        .param("endDate", "2026-11-01")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/focus-sessions")
                        .param("startDate", java.time.LocalDate.now().toString())
                        .param("endDate", java.time.LocalDate.now().toString())
                        .with(httpBasic(OTHER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private String focusRequest(String key, Long subject, Long goal, int minutes) {
        return focusRequest(key, subject, goal, minutes, FOCUS_DURATION_MINUTES);
    }

    private String focusRequest(String key, Long subject, Long goal, int minutes, long elapsedMinutes) {
        Instant completedAt = Instant.now().minusSeconds(1);
        Instant startedAt = completedAt.minusSeconds(elapsedMinutes * 60);
        return """
                {
                  "subjectId": %d,
                  "goalId": %s,
                  "durationMinutes": %d,
                  "startedAt": "%s",
                  "completedAt": "%s",
                  "requestKey": "%s"
                }
                """.formatted(
                subject,
                goal == null ? "null" : goal.toString(),
                minutes,
                startedAt,
                completedAt,
                key);
    }

    private static final long FOCUS_DURATION_MINUTES = 26;
}
