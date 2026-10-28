package com.personalrecords.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PersonalRecordApiApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthEndpointIsPublicAndReturnsHealthyStatus() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(content().json("{\"status\":\"healthy\"}"));
	}

	@Test
	void protectedEndpointRejectsUnauthenticatedRequest() throws Exception {
		mockMvc.perform(get("/api/protected-test"))
				.andExpect(status().isUnauthorized());
	}
}
