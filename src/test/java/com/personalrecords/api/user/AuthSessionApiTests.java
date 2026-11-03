package com.personalrecords.api.user;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSessionApiTests {

    private static final String EMAIL = "session@example.com";
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpUser() {
        userRepository.findByNormalizedEmail(EMAIL).orElseGet(() ->
                userRepository.saveAndFlush(
                        new User("Session Student", EMAIL, passwordEncoder.encode(PASSWORD))));
    }

    @Test
    void sessionSurvivesProtectedRequestsAndLogoutInvalidatesIt() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = csrfResult.getResponse().getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        assertNotNull(session);
        assertFalse(csrfToken.isBlank());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType("application/json")
                        .content("{\"email\":\"SESSION@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(authenticatedSession);

        mockMvc.perform(get("/api/auth/session").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        MvcResult logoutCsrfResult = mockMvc.perform(get("/api/csrf").session(authenticatedSession))
                .andExpect(status().isOk())
                .andReturn();
        String logoutToken = logoutCsrfResult.getResponse().getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/api/auth/logout")
                        .session(authenticatedSession)
                        .header("X-CSRF-TOKEN", logoutToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsInvalidCredentialsWithoutCreatingAnAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"incorrect\"}")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    @Test
    void sessionEndpointRequiresAuthenticationAndCsrfEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    void anInvalidatedSessionCannotAccessProtectedEndpoints() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/csrf")).andReturn();
        String csrfToken = csrfResult.getResponse().getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType("application/json")
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult.getRequest().getSession(false);

        authenticatedSession.invalidate();
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());
    }
}
