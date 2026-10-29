package com.personalrecords.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.personalrecords.api.records.RecordCategory;
import com.personalrecords.api.records.RecordStatus;
import com.personalrecords.api.records.StudentRecordRepository;
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
class StudentRecordApiTests {

    private static final String PASSWORD = "StrongPassword123!";
    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String OTHER_EMAIL = "other@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRecordRepository recordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpUsers() {
        recordRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.saveAndFlush(new User("Record Owner", OWNER_EMAIL, passwordEncoder.encode(PASSWORD)));
        userRepository.saveAndFlush(new User("Other User", OTHER_EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    @Test
    void authenticatedUserCanCreateRecordWithoutExposingAccountInformation() throws Exception {
        createRecord(OWNER_EMAIL, validRequest("Database Systems"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Database Systems"))
                .andExpect(jsonPath("$.category").value("ACADEMIC"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.*", hasSize(10)));
    }

    @Test
    void authenticatedUserCanListOnlyTheirOwnRecords() throws Exception {
        createRecord(OWNER_EMAIL, validRequest("Owner record")).andExpect(status().isCreated());
        createRecord(OTHER_EMAIL, validRequest("Other record")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/records").with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Owner record"));
    }

    @Test
    void authenticatedUserCanRetrieveTheirOwnRecord() throws Exception {
        long id = createRecordAndGetId(OWNER_EMAIL, "My record");

        mockMvc.perform(get("/api/records/{id}", id).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("My record"));
    }

    @Test
    void authenticatedUserCanUpdateTheirOwnRecord() throws Exception {
        long id = createRecordAndGetId(OWNER_EMAIL, "Before update");

        mockMvc.perform(put("/api/records/{id}", id)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRequest("After update")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("After update"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void authenticatedUserCanDeleteTheirOwnRecord() throws Exception {
        long id = createRecordAndGetId(OWNER_EMAIL, "Delete me");

        mockMvc.perform(delete("/api/records/{id}", id)
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/records/{id}", id).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestCannotAccessRecords() throws Exception {
        mockMvc.perform(get("/api/records"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void userCannotRetrieveAnotherUsersRecord() throws Exception {
        long id = createRecordAndGetId(OWNER_EMAIL, "Private record");

        mockMvc.perform(get("/api/records/{id}", id).with(httpBasic(OTHER_EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotUpdateAnotherUsersRecord() throws Exception {
        long id = createRecordAndGetId(OWNER_EMAIL, "Owner's record");

        mockMvc.perform(put("/api/records/{id}", id)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRequest("Modified by another user")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/records/{id}", id).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(jsonPath("$.title").value("Owner's record"));
    }

    @Test
    void userCannotDeleteAnotherUsersRecord() throws Exception {
        long id = createRecordAndGetId(OWNER_EMAIL, "Keep this record");

        mockMvc.perform(delete("/api/records/{id}", id)
                        .with(httpBasic(OTHER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/records/{id}", id).with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void missingOrInvalidRequiredFieldsReturnValidationErrors() throws Exception {
        mockMvc.perform(post("/api/records")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"description\":\"Missing required fields\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.category").exists())
                .andExpect(jsonPath("$.fieldErrors.status").exists());

        mockMvc.perform(post("/api/records")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRequest(" ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void invalidDateRangeReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/records")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"Bad dates\",\"category\":\"PROJECT\","
                                + "\"status\":\"PLANNED\",\"startDate\":\"2026-06-01\","
                                + "\"endDate\":\"2026-05-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateRange").exists());
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/api/records")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .contentType("application/json")
                        .content(validRequest("CSRF protected")))
                .andExpect(status().isForbidden());

        createRecord(OWNER_EMAIL, validRequest("CSRF token supplied"))
                .andExpect(status().isCreated());
    }

    @Test
    void authenticatedApiClientCanObtainCsrfToken() throws Exception {
        mockMvc.perform(get("/api/csrf").with(httpBasic(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    private ResultActions createRecord(String email, String requestBody) throws Exception {
        return mockMvc.perform(post("/api/records")
                .with(httpBasic(email, PASSWORD))
                .with(csrf())
                .contentType("application/json")
                .content(requestBody));
    }

    private String validRequest(String title) throws Exception {
        return objectMapper.writeValueAsString(new com.personalrecords.api.records.StudentRecordRequest(
                title,
                "A student progress record",
                RecordCategory.ACADEMIC,
                "Example University",
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 5, 30),
                RecordStatus.COMPLETED));
    }

    private long createRecordAndGetId(String email, String title) throws Exception {
        ResultActions result = createRecord(email, validRequest(title))
                .andExpect(status().isCreated());
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id")
                .asLong();
    }
}
