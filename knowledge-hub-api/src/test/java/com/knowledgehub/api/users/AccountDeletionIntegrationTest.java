package com.knowledgehub.api.users;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowledgehub.api.TestcontainersConfiguration;
import com.knowledgehub.api.storage.ObjectStorage;
import com.knowledgehub.api.storage.ObjectStorageException;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
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
@Import({TestcontainersConfiguration.class, AccountDeletionIntegrationTest.ProtectedController.class})
@AutoConfigureMockMvc
@SpringBootTest
class AccountDeletionIntegrationTest {

	private static final String EMAIL = "delete@example.com";
	private static final String PASSWORD = "account-password-123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AccountDeletionJobProcessor jobProcessor;

	@Autowired
	private ObjectStorage objectStorage;

	@BeforeEach
	void clearUsers() {
		jdbcTemplate.update("delete from users");
	}

	@Test
	void confirmedDeletionMarksPendingRevokesTokensAndEnqueuesCleanup() throws Exception {
		register();
		UUID userId = jdbcTemplate.queryForObject(
				"select id from users where email = ?", UUID.class, EMAIL);
		byte[] content = "private document".getBytes(StandardCharsets.UTF_8);
		objectStorage.put(
				userId,
				"documents/private",
				new ByteArrayInputStream(content),
				content.length,
				"text/plain");
		MvcResult login = login();
		JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsByteArray());
		String accessToken = loginBody.get("accessToken").asText();
		Cookie refreshToken = login.getResponse().getCookie("refresh_token");

		mockMvc.perform(delete("/api/v1/account")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("password", PASSWORD, "confirmation", "DELETE"))))
				.andExpect(status().isAccepted());

		org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
				"select status from users where email = ?", String.class, EMAIL))
				.isEqualTo("DELETION_PENDING");
		org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
				"select count(*) from account_deletion_jobs", Integer.class))
				.isEqualTo(1);

		mockMvc.perform(get("/api/v1/test/account-protected")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(refreshToken))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
				.content(credentials()))
				.andExpect(status().isUnauthorized());

		jobProcessor.processNext();
		org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
				"select count(*) from users where email = ?", Integer.class, EMAIL))
				.isZero();
		assertThatThrownBy(() -> objectStorage.createDownloadUrl(
						userId, "documents/private", Duration.ofMinutes(5)))
				.isInstanceOf(ObjectStorageException.class);
	}

	@Test
	void deletionRequiresPasswordAndExactTypedConfirmation() throws Exception {
		register();
		String accessToken = objectMapper
				.readTree(login().getResponse().getContentAsByteArray())
				.get("accessToken")
				.asText();

		mockMvc.perform(delete("/api/v1/account")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("password", PASSWORD, "confirmation", "delete"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_CONFIRMATION_INVALID"));

		mockMvc.perform(delete("/api/v1/account")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("password", "incorrect-password", "confirmation", "DELETE"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	private void register() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials()))
				.andExpect(status().isCreated());
	}

	private MvcResult login() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials()))
				.andExpect(status().isOk())
				.andReturn();
	}

	private String credentials() {
		return objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD));
	}

	@RestController
	static class ProtectedController {

		@GetMapping("/api/v1/test/account-protected")
		Map<String, Boolean> protectedRoute() {
			return Map.of("ok", true);
		}
	}
}
