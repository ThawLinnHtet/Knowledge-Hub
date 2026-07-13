package com.knowledgehub.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowledgehub.api.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import({
	TestcontainersConfiguration.class,
	PasswordResetIntegrationTest.RecordingMailConfiguration.class
})
@AutoConfigureMockMvc
@SpringBootTest
class PasswordResetIntegrationTest {

	private static final String EMAIL = "reset@example.com";
	private static final String OLD_PASSWORD = "original-password-123";
	private static final String NEW_PASSWORD = "replacement-password-456";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RecordingMailService mailService;

	@BeforeEach
	void resetState() {
		jdbcTemplate.update("delete from users");
		mailService.clear();
	}

	@Test
	void resetTokenIsHashedSingleUseAndRevokesRefreshSessions() throws Exception {
		register(EMAIL, OLD_PASSWORD);
		Cookie refreshCookie = login(EMAIL, OLD_PASSWORD)
				.getResponse()
				.getCookie("refresh_token");

		requestReset(EMAIL).andExpect(status().isAccepted());
		String resetToken = mailService.tokenFor(EMAIL);
		assertThat(resetToken).isNotBlank();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from password_reset_tokens where token_hash = ?",
				Integer.class,
				resetToken))
				.isZero();

		consumeReset(resetToken, NEW_PASSWORD).andExpect(status().isNoContent());

		loginRequest(EMAIL, OLD_PASSWORD).andExpect(status().isUnauthorized());
		loginRequest(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(refreshCookie))
				.andExpect(status().isUnauthorized());
		consumeReset(resetToken, NEW_PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
	}

	@Test
	void expiredTokenIsRejectedAndUnknownEmailDoesNotRevealAccountState() throws Exception {
		register(EMAIL, OLD_PASSWORD);
		requestReset(EMAIL).andExpect(status().isAccepted());
		String resetToken = mailService.tokenFor(EMAIL);
		jdbcTemplate.update("update password_reset_tokens set expires_at = now() - interval '1 minute'");

		consumeReset(resetToken, NEW_PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));

		requestReset("unknown@example.com").andExpect(status().isAccepted());
		assertThat(mailService.tokenFor("unknown@example.com")).isNull();
	}

	private void register(String email, String password) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email, password)))
				.andExpect(status().isCreated());
	}

	private MvcResult login(String email, String password) throws Exception {
		return loginRequest(email, password).andExpect(status().isOk()).andReturn();
	}

	private org.springframework.test.web.servlet.ResultActions loginRequest(
			String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, password)));
	}

	private org.springframework.test.web.servlet.ResultActions requestReset(String email)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/forgot-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", email))));
	}

	private org.springframework.test.web.servlet.ResultActions consumeReset(
			String token, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("token", token, "password", password))));
	}

	private String credentials(String email, String password) {
		return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingMailConfiguration {

		@Bean
		@Primary
		RecordingMailService recordingMailService() {
			return new RecordingMailService();
		}
	}

	static class RecordingMailService implements MailService {

		private final Map<String, String> tokens = new ConcurrentHashMap<>();

		@Override
		public void sendPasswordReset(String email, String rawToken) {
			tokens.put(email, rawToken);
		}

		String tokenFor(String email) {
			return tokens.get(email);
		}

		void clear() {
			tokens.clear();
		}
	}
}
