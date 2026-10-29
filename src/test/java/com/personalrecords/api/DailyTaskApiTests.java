package com.personalrecords.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DailyTaskApiTests {

    private static final String PASSWORD = "StrongPassword123!";
    private static final String OWNER_EMAIL = "task-owner@example.com";
    private static final String OTHER_EMAIL = "task-other@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyTaskRepository taskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpUsers() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.saveAndFlush(new User("Task Owner", OWNER_EMAIL, passwordEncoder.encode(PASSWORD)));
        userRepository.saveAndFlush(new User("Other User", OTHER_EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    @Test
    void authenticatedUserCanCreateTaskWithPendingStatusAndSafeResponse() throws Exception {
        createTask(OWNER_EMAIL, validRequest("Review algorithms", LocalDate.now()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("Review algorithms"))
                .andExpect(jsonPath("$.subject").value("Computer Science"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.actualDurationMinutes").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void userCanListOwnTasksAndFilterByDateAndInclusiveDateRange() throws Exception {
        createTaskAndGetId(OWNER_EMAIL, "Earlier task", LocalDate.now().minusDays(1));
        createTaskAndGetId(OWNER_EMAIL, "Today task", LocalDate.now());
        createTaskAndGetId(OWNER_EMAIL, "Other user's task", LocalDate.now());
        createTaskAndGetId(OTHER_EMAIL, "Private task", LocalDate.now());

        mockMvc.perform(get("/api/tasks").with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(get("/api/tasks").param("date", LocalDate.now().toString())
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/tasks")
                        .param("startDate", LocalDate.now().minusDays(1).toString())
                        .param("endDate", LocalDate.now().toString())
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void authenticatedUserCanRetrieveAndUpdateTheirTaskAndReschedulingPreservesIdAndHistory() throws Exception {
        LocalDate originalDate = LocalDate.now().plusDays(2);
        long id = createTaskAndGetId(OWNER_EMAIL, "Original task", originalDate);
        String updatedRequest = validRequest("Rescheduled task", originalDate.plusDays(3));

        mockMvc.perform(get("/api/tasks/{id}", id).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.plannedDate").value(originalDate.toString()));

        mockMvc.perform(put("/api/tasks/{id}", id)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updatedRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Rescheduled task"))
                .andExpect(jsonPath("$.plannedDate").value(originalDate.plusDays(3).toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void statusAndActualDurationAreUpdatedIndependently() throws Exception {
        long id = createTaskAndGetId(OWNER_EMAIL, "Study session", LocalDate.now());

        mockMvc.perform(patch("/api/tasks/{id}/status", id)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualDurationMinutes").doesNotExist());

        mockMvc.perform(patch("/api/tasks/{id}/actual-duration", id)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"actualDurationMinutes\":35}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualDurationMinutes").value(35));
    }

    @Test
    void authenticatedUserCanDeleteTheirTask() throws Exception {
        long id = createTaskAndGetId(OWNER_EMAIL, "Remove task", LocalDate.now());
        mockMvc.perform(delete("/api/tasks/{id}", id)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(get("/api/tasks/{id}", id).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsCannotAccessTasks() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tasks/today-summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReadUpdateStatusUpdateDurationOrDeleteAnotherUsersTask() throws Exception {
        long id = createTaskAndGetId(OWNER_EMAIL, "Private task", LocalDate.now());

        mockMvc.perform(get("/api/tasks/{id}", id).with(httpBasic(OTHER_EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tasks/{id}", id)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRequest("Changed by other user", LocalDate.now())))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tasks/{id}/status", id)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tasks/{id}/actual-duration", id)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"actualDurationMinutes\":20}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/{id}", id)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/tasks/{id}", id).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(jsonPath("$.title").value("Private task"));
    }

    @Test
    void missingRequiredFieldsAndInvalidDurationsReturnValidationErrors() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"description\":\"missing fields\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.subject").exists())
                .andExpect(jsonPath("$.fieldErrors.plannedDate").exists())
                .andExpect(jsonPath("$.fieldErrors.estimatedDurationMinutes").exists());

        mockMvc.perform(post("/api/tasks")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRequest("Invalid estimate", LocalDate.now(), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.estimatedDurationMinutes").exists());

        long id = createTaskAndGetId(OWNER_EMAIL, "Valid task", LocalDate.now());
        mockMvc.perform(patch("/api/tasks/{id}/actual-duration", id)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"actualDurationMinutes\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.actualDurationMinutes").exists());
    }

    @Test
    void invalidDateFilterCombinationsAndMalformedDatesReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("date", LocalDate.now().toString())
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().toString())
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").exists());
        mockMvc.perform(get("/api/tasks")
                        .param("startDate", LocalDate.now().toString())
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
        mockMvc.perform(get("/api/tasks")
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().minusDays(1).toString())
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists());
        mockMvc.perform(get("/api/tasks")
                        .param("date", "not-a-date")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").exists());
    }

    @Test
    void todaySummaryUsesOnlyAuthenticatedUsersTasksAndDoesNotInventActualTime() throws Exception {
        long completedId = createTaskAndGetId(OWNER_EMAIL, "Complete", LocalDate.now(), 40);
        createTaskAndGetId(OWNER_EMAIL, "Pending", LocalDate.now(), 35);
        createTaskAndGetId(OWNER_EMAIL, "Tomorrow", LocalDate.now().plusDays(1), 100);
        createTaskAndGetId(OTHER_EMAIL, "Other user's today task", LocalDate.now(), 500);
        mockMvc.perform(patch("/api/tasks/{id}/status", completedId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/tasks/{id}/actual-duration", completedId)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"actualDurationMinutes\":25}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/today-summary").with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.taskCount").value(2))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.pendingOrInProgressCount").value(1))
                .andExpect(jsonPath("$.plannedMinutes").value(75))
                .andExpect(jsonPath("$.actualMinutes").value(25));
    }

    private ResultActions createTask(String email, String requestBody) throws Exception {
        return mockMvc.perform(post("/api/tasks")
                .with(httpBasic(email, PASSWORD))
                .with(csrf())
                .contentType("application/json")
                .content(requestBody));
    }

    private long createTaskAndGetId(String email, String title, LocalDate plannedDate) throws Exception {
        return createTaskAndGetId(email, title, plannedDate, 60);
    }

    private long createTaskAndGetId(String email, String title, LocalDate plannedDate, int estimate) throws Exception {
        ResultActions result = createTask(email, validRequest(title, plannedDate, estimate))
                .andExpect(status().isCreated());
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id")
                .asLong();
    }

    private String validRequest(String title, LocalDate plannedDate) throws Exception {
        return validRequest(title, plannedDate, 60);
    }

    private String validRequest(String title, LocalDate plannedDate, int estimate) throws Exception {
        return objectMapper.writeValueAsString(new com.personalrecords.api.tasks.DailyTaskRequest(
                title, "Review the lesson material", "Computer Science", plannedDate, estimate));
    }
}
