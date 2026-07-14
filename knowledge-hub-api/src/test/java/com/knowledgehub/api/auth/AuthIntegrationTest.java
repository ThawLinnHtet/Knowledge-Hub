package com.knowledgehub.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowledgehub.api.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, AuthIntegrationTest.ProtectedController.class})
@AutoConfigureMockMvc
@SpringBootTest
class AuthIntegrationTest {

	private static final String EMAIL = "reader@example.com";
	private static final String PASSWORD = "correct-horse-battery-staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clearUsers() {
		jdbcTemplate.update("delete from users");
	}

	@Test
	void registerLoginRotateRefreshLogoutAndAuthorizeBearerToken() throws Exception {
		MvcResult registration = register(EMAIL, PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(EMAIL))
				.andReturn();
		String userId = body(registration).get("id").asText();

		register(EMAIL.toUpperCase(), PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_UNAVAILABLE"));

		login(EMAIL, "wrong-password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		MvcResult login = login(EMAIL, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(cookie().httpOnly("refresh_token", true))
				.andReturn();
		Cookie firstRefreshToken = login.getResponse().getCookie("refresh_token");
		String firstAccessToken = body(login).get("accessToken").asText();

		mockMvc.perform(get("/api/v1/test/protected")
						.header("Authorization", "Bearer " + firstAccessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subject").value(userId));

		MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(firstRefreshToken))
				.andExpect(status().isOk())
				.andReturn();
		Cookie secondRefreshToken = refresh.getResponse().getCookie("refresh_token");
		String secondAccessToken = body(refresh).get("accessToken").asText();

		assertThat(secondRefreshToken).isNotNull();
		assertThat(secondRefreshToken.getValue()).isNotEqualTo(firstRefreshToken.getValue());
		assertThat(secondAccessToken).isNotEqualTo(firstAccessToken);

		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(firstRefreshToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

		mockMvc.perform(post("/api/v1/auth/logout")
						.with(csrf())
						.cookie(secondRefreshToken))
				.andExpect(status().isNoContent())
				.andExpect(cookie().maxAge("refresh_token", 0));

		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(secondRefreshToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshRequiresCsrfProtection() throws Exception {
		register(EMAIL, PASSWORD).andExpect(status().isCreated());
		Cookie refreshToken = login(EMAIL, PASSWORD)
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getCookie("refresh_token");

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void protectedApiRejectsMissingBearerTokenWithStandardError() throws Exception {
		mockMvc.perform(get("/api/v1/test/protected"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	private org.springframework.test.web.servlet.ResultActions register(String email, String password)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new Credentials(email, password))));
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new Credentials(email, password))));
	}

	private JsonNode body(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}

	record Credentials(String email, String password) {}

	@RestController
	static class ProtectedController {

		@GetMapping("/api/v1/test/protected")
		SubjectResponse protectedResource(Principal principal) {
			return new SubjectResponse(principal.getName());
		}
	}

	record SubjectResponse(String subject) {}
}
