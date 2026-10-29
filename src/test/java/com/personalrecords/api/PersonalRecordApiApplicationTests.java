package com.personalrecords.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PersonalRecordApiApplicationTests {

    private static final String PASSWORD = "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    @Test
    void testDatabaseIsIsolatedFromLocalPersistentDatabase() {
        org.junit.jupiter.api.Assertions.assertEquals(
                "jdbc:h2:mem:personalrecords-test;DB_CLOSE_DELAY=-1", datasourceUrl);
    }

    @Test
    void healthEndpointIsPublicAndReturnsHealthyStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("{\"status\":\"healthy\"}"));
    }

    @Test
    void registrationReturnsOnlySafeFieldsAndStoresAnEncodedPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registrationBody("Arun Kumar", "ARUN@example.com", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Arun Kumar"))
                .andExpect(jsonPath("$.email").value("arun@example.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User user = userRepository.findByNormalizedEmail(" ARUN@EXAMPLE.COM ").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(PASSWORD, user.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));
        String entityJson = objectMapper.writeValueAsString(user);
        org.junit.jupiter.api.Assertions.assertFalse(entityJson.contains("passwordHash"));
    }

    @Test
    void duplicateEmailReturnsConflictEvenWhenCaseDiffers() throws Exception {
        register("Arun", "arun@example.com", PASSWORD)
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registrationBody("Another Arun", "ARUN@EXAMPLE.COM", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void invalidEmailReturnsBadRequest() throws Exception {
        register("Arun", "not-an-email", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void blankAndOverlongNamesReturnBadRequest() throws Exception {
        register(" ", "arun@example.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        register("N".repeat(101), "arun@example.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void missingPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"name\":\"Arun\",\"email\":\"arun@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void passwordPolicyViolationsReturnBadRequest() throws Exception {
        register("Arun", "arun@example.com", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        register("Arun", "arun@example.com", "😀".repeat(19))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void malformedOrMissingRequestBodyReturnsSafeBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void registeredUserCanAuthenticateWithValidCredentials() throws Exception {
        register("Arun", "arun@example.com", PASSWORD)
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/protected-test")
                        .with(httpBasic("ARUN@EXAMPLE.COM", PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void incorrectCredentialsAreRejected() throws Exception {
        register("Arun", "arun@example.com", PASSWORD)
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/protected-test")
                        .with(httpBasic("arun@example.com", "incorrect-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void unauthenticatedProtectedRequestIsRejectedAndRegistrationIsPublic() throws Exception {
        mockMvc.perform(get("/api/protected-test"))
                .andExpect(status().isUnauthorized());

        register("Arun", "arun@example.com", PASSWORD)
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String name, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(registrationBody(name, email, password)));
    }

    private String registrationBody(String name, String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "email", email,
                "password", password));
    }
}
