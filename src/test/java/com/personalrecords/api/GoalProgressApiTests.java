package com.personalrecords.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

import com.personalrecords.api.goals.Goal;
import com.personalrecords.api.goals.GoalCategory;
import com.personalrecords.api.goals.GoalRepository;
import com.personalrecords.api.goals.GoalRequest;
import com.personalrecords.api.goals.GoalStatus;
import com.personalrecords.api.goals.ProgressLogRepository;
import com.personalrecords.api.goals.ProgressLogRequest;
import com.personalrecords.api.records.StudentRecordRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class GoalProgressApiTests {

    private static final String PASSWORD = "StrongPassword123!";
    private static final String OWNER_EMAIL = "goal-owner@example.com";
    private static final String OTHER_EMAIL = "goal-other@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRecordRepository recordRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private ProgressLogRepository logRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpUsers() {
        logRepository.deleteAll();
        goalRepository.deleteAll();
        recordRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.saveAndFlush(new User("Goal Owner", OWNER_EMAIL, passwordEncoder.encode(PASSWORD)));
        userRepository.saveAndFlush(new User("Other User", OTHER_EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    @AfterEach
    void clearGoals() {
        logRepository.deleteAll();
        goalRepository.deleteAll();
    }

    @Test
    void authenticatedUserCanCreateRetrieveListUpdateAndDeleteGoals() throws Exception {
        long goalId = createGoalAndGetId(OWNER_EMAIL, goalRequest("Learn databases", 300));

        mockMvc.perform(get("/api/goals/{id}", goalId).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Learn databases"))
                .andExpect(jsonPath("$.category").value("STUDY"))
                .andExpect(jsonPath("$.targetDurationMinutes").value(300))
                .andExpect(jsonPath("$.preferredWeekdays", hasSize(2)))
                .andExpect(jsonPath("$.preferredWeekdays[0]").value("MONDAY"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/goals").with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/goals").with(httpBasic(OTHER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(put("/api/goals/{id}", goalId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                goalRequest("Finish databases", 450, Set.of(DayOfWeek.FRIDAY)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Finish databases"))
                .andExpect(jsonPath("$.targetDurationMinutes").value(450))
                .andExpect(jsonPath("$.preferredWeekdays", hasSize(1)))
                .andExpect(jsonPath("$.preferredWeekdays[0]").value("FRIDAY"));

        mockMvc.perform(delete("/api/goals/{id}", goalId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/goals/{id}", goalId).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidGoalFieldsAndNonpositiveTargetDurationAreRejected() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"No target\",\"category\":\"STUDY\",\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.targetDurationMinutes").exists());

        mockMvc.perform(post("/api/goals")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(goalRequest("Zero target", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.targetDurationMinutes").exists());

        mockMvc.perform(post("/api/goals")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\" \",\"category\":\"STUDY\","
                                + "\"targetDurationMinutes\":60,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void userCannotReadUpdateOrDeleteAnotherUsersGoal() throws Exception {
        long goalId = createGoalAndGetId(OWNER_EMAIL, goalRequest("Private goal", 60));

        mockMvc.perform(get("/api/goals/{id}", goalId).with(httpBasic(OTHER_EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/goals/{id}", goalId)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(goalRequest("Tampered", 60))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/goals/{id}", goalId)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/goals/{id}", goalId).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Private goal"));
    }

    @Test
    void authenticatedUserCanCreateRetrieveListUpdateAndDeleteProgressLogs() throws Exception {
        long goalId = createGoalAndGetId(OWNER_EMAIL, goalRequest("Historical study", 500));
        long logId = createLogAndGetId(
                OWNER_EMAIL, goalId, logRequest(LocalDate.of(2001, 2, 3), 45, "Historical session"));

        mockMvc.perform(get("/api/goals/{goalId}/logs", goalId).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].activityDate").value("2001-02-03"));

        mockMvc.perform(get("/api/goals/{goalId}/logs/{logId}", goalId, logId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSpentMinutes").value(45))
                .andExpect(jsonPath("$.goalId").value(goalId))
                .andExpect(jsonPath("$.goal").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(put("/api/goals/{goalId}/logs/{logId}", goalId, logId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                logRequest(LocalDate.of(2001, 2, 4), 75, "Updated historical session"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSpentMinutes").value(75))
                .andExpect(jsonPath("$.activityDate").value("2001-02-04"))
                .andExpect(jsonPath("$.summary").value("Updated historical session"));

        mockMvc.perform(delete("/api/goals/{goalId}/logs/{logId}", goalId, logId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/goals/{goalId}/logs/{logId}", goalId, logId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidProgressLogDataIsRejected() throws Exception {
        long goalId = createGoalAndGetId(OWNER_EMAIL, goalRequest("Validate logs", 100));

        mockMvc.perform(post("/api/goals/{goalId}/logs", goalId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"activityDate\":\"2026-01-01\",\"timeSpentMinutes\":0,"
                                + "\"summary\":\"Invalid duration\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.timeSpentMinutes").exists());

        mockMvc.perform(post("/api/goals/{goalId}/logs", goalId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"timeSpentMinutes\":10,\"summary\":\"Missing date\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.activityDate").exists());

        mockMvc.perform(post("/api/goals/{goalId}/logs", goalId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"activityDate\":\"2026-01-01\","
                                + "\"timeSpentMinutes\":10,\"summary\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.summary").exists());
    }

    @Test
    void userCannotCreateOrAccessLogsForAnotherUsersGoal() throws Exception {
        long ownerGoalId = createGoalAndGetId(OWNER_EMAIL, goalRequest("Owner goal", 100));
        long ownerLogId = createLogAndGetId(
                OWNER_EMAIL, ownerGoalId, logRequest(LocalDate.of(2020, 1, 1), 20, "Owner log"));

        mockMvc.perform(post("/api/goals/{goalId}/logs", ownerGoalId)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                logRequest(LocalDate.now(), 15, "Unauthorized log"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/goals/{goalId}/logs", ownerGoalId)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/goals/{goalId}/logs/{logId}", ownerGoalId, ownerLogId)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/goals/{goalId}/logs/{logId}", ownerGoalId, ownerLogId)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                logRequest(LocalDate.now(), 50, "Tampered log"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/goals/{goalId}/logs/{logId}", ownerGoalId, ownerLogId)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/goals/{goalId}/logs/{logId}", ownerGoalId, ownerLogId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Owner log"));
    }

    @Test
    void unauthenticatedRequestsCannotAccessGoalsOrLogs() throws Exception {
        mockMvc.perform(get("/api/goals")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/goals/1/logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void progressSummaryUsesIsoWeekAndCalendarMonthBoundaries() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);
        List<ProgressEntry> entries = List.of(
                new ProgressEntry(today, 30),
                new ProgressEntry(weekStart, 40),
                new ProgressEntry(weekStart.minusDays(1), 20),
                new ProgressEntry(monthStart, 25),
                new ProgressEntry(monthStart.minusDays(1), 15),
                new ProgressEntry(LocalDate.of(1900, 1, 1), 10));
        long total = entries.stream().mapToLong(ProgressEntry::minutes).sum();
        long weekMinutes = entries.stream()
                .filter(entry -> !entry.date().isBefore(weekStart) && entry.date().isBefore(weekStart.plusWeeks(1)))
                .mapToLong(ProgressEntry::minutes).sum();
        long monthMinutes = entries.stream()
                .filter(entry -> !entry.date().isBefore(monthStart) && entry.date().isBefore(monthStart.plusMonths(1)))
                .mapToLong(ProgressEntry::minutes).sum();
        long goalId = createGoalAndGetId(OWNER_EMAIL, goalRequest("Boundary summary", 200));
        for (ProgressEntry entry : entries) {
            createLog(OWNER_EMAIL, goalId, logRequest(entry.date(), entry.minutes(), "Study session"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/goals/{id}/progress", goalId).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(goalId))
                .andExpect(jsonPath("$.goalTitle").value("Boundary summary"))
                .andExpect(jsonPath("$.targetDurationMinutes").value(200))
                .andExpect(jsonPath("$.totalRecordedMinutes").value(total))
                .andExpect(jsonPath("$.remainingMinutes").value(200 - total))
                .andExpect(jsonPath("$.progressPercentage").value(70.00))
                .andExpect(jsonPath("$.totalLogCount").value(entries.size()))
                .andExpect(jsonPath("$.currentWeekMinutes").value(weekMinutes))
                .andExpect(jsonPath("$.currentMonthMinutes").value(monthMinutes))
                .andExpect(jsonPath("$.mostRecentActivityDate").value(today.toString()));
    }

    @Test
    void progressPercentageIsCappedAndRemainingMinutesNeverGoBelowZero() throws Exception {
        long goalId = createGoalAndGetId(OWNER_EMAIL, goalRequest("Capped progress", 100));
        createLog(OWNER_EMAIL, goalId, logRequest(LocalDate.now(), 125, "Exceeded target"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/goals/{id}/progress", goalId).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecordedMinutes").value(125))
                .andExpect(jsonPath("$.remainingMinutes").value(0))
                .andExpect(jsonPath("$.progressPercentage").value(100.00));
    }

    @Test
    void updatingAndDeletingLogsImmediatelyChangesProgressTotals() throws Exception {
        long goalId = createGoalAndGetId(OWNER_EMAIL, goalRequest("Mutable totals", 200));
        long firstLogId = createLogAndGetId(
                OWNER_EMAIL, goalId, logRequest(LocalDate.now(), 30, "First session"));
        createLog(OWNER_EMAIL, goalId, logRequest(LocalDate.now(), 20, "Second session"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/goals/{goalId}/logs/{logId}", goalId, firstLogId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                logRequest(LocalDate.now(), 50, "Updated first session"))))
                .andExpect(status().isOk());
        assertTotalMinutes(goalId, 70, 2);

        mockMvc.perform(delete("/api/goals/{goalId}/logs/{logId}", goalId, firstLogId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertTotalMinutes(goalId, 20, 1);
    }

    @Test
    void deletingGoalAlsoRemovesItsProgressLogs() throws Exception {
        long goalId = createGoalAndGetId(OWNER_EMAIL, goalRequest("Goal with logs", 60));
        createLog(OWNER_EMAIL, goalId, logRequest(LocalDate.now(), 10, "Dependent log"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/goals/{id}", goalId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(logRepository.findAll().isEmpty());
    }

    private void assertTotalMinutes(long goalId, long total, int logCount) throws Exception {
        mockMvc.perform(get("/api/goals/{id}/progress", goalId).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecordedMinutes").value(total))
                .andExpect(jsonPath("$.totalLogCount").value(logCount))
                .andExpect(jsonPath("$.progressPercentage").value(total * 100.0 / 200));
    }

    private long createGoalAndGetId(String email, GoalRequest request) throws Exception {
        ResultActions result = createGoal(email, request).andExpect(status().isCreated());
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id")
                .asLong();
    }

    private ResultActions createGoal(String email, GoalRequest request) throws Exception {
        return mockMvc.perform(post("/api/goals")
                .with(httpBasic(email, PASSWORD))
                .with(csrf())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));
    }

    private GoalRequest goalRequest(String title, int targetDurationMinutes) {
        return goalRequest(title, targetDurationMinutes, Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
    }

    private GoalRequest goalRequest(
            String title, int targetDurationMinutes, Set<DayOfWeek> preferredWeekdays) {
        return new GoalRequest(
                title,
                "Work toward a learning objective",
                GoalCategory.STUDY,
                LocalDate.now().plusMonths(1),
                targetDurationMinutes,
                preferredWeekdays,
                GoalStatus.IN_PROGRESS);
    }

    private long createLogAndGetId(String email, long goalId, ProgressLogRequest request) throws Exception {
        ResultActions result = createLog(email, goalId, request).andExpect(status().isCreated());
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id")
                .asLong();
    }

    private ResultActions createLog(String email, long goalId, ProgressLogRequest request) throws Exception {
        return mockMvc.perform(post("/api/goals/{goalId}/logs", goalId)
                .with(httpBasic(email, PASSWORD))
                .with(csrf())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));
    }

    private ProgressLogRequest logRequest(LocalDate activityDate, int minutes, String summary) {
        return new ProgressLogRequest(activityDate, minutes, summary, "Session notes");
    }

    private record ProgressEntry(LocalDate date, int minutes) {
    }
}
