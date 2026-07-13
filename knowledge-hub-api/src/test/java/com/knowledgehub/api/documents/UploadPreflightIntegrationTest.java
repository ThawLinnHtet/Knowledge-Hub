package com.knowledgehub.api.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowledgehub.api.TestcontainersConfiguration;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = "app.upload.max-file-size-bytes=64")
class UploadPreflightIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery-staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UploadConfirmationTokenService confirmationTokenService;

	@BeforeEach
	void clearUsers() {
		jdbcTemplate.update("delete from users");
	}

	@Test
	void authenticatedUserCanPreflightBatchWithPerFileValidationResults() throws Exception {
		String accessToken = registerAndLogin("uploader@example.com");
		MockMultipartFile accepted = file("notes.txt", "application/pdf", "trusted notes");
		MockMultipartFile unsupported = file("program.exe", "application/octet-stream", "binary");
		MockMultipartFile mismatched = file("renamed.pdf", MediaType.APPLICATION_PDF_VALUE, "plain text");
		MockMultipartFile oversized = new MockMultipartFile(
				"files", "large.txt", MediaType.TEXT_PLAIN_VALUE, new byte[65]);

		mockMvc.perform(multipart("/api/v1/documents/uploads/preflight")
					.file(accepted)
					.file(unsupported)
					.file(mismatched)
					.file(oversized)
					.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(4))
				.andExpect(jsonPath("$.items[0].filename").value("notes.txt"))
				.andExpect(jsonPath("$.items[0].status").value("ACCEPTED"))
				.andExpect(jsonPath("$.items[0].detectedMediaType").value("text/plain"))
				.andExpect(jsonPath("$.items[0].sha256Hash").isNotEmpty())
				.andExpect(jsonPath("$.items[0].error").doesNotExist())
				.andExpect(jsonPath("$.items[1].status").value("REJECTED"))
				.andExpect(jsonPath("$.items[1].error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.items[1].error.requestId").isNotEmpty())
				.andExpect(jsonPath("$.items[2].status").value("REJECTED"))
				.andExpect(jsonPath("$.items[2].error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.items[3].status").value("REJECTED"))
				.andExpect(jsonPath("$.items[3].error.code").value("LIMIT_EXCEEDED"));
	}

	@Test
	void repeatedContentWithinOneBatchRequiresDuplicateConfirmation() throws Exception {
		String accessToken = registerAndLogin("batch@example.com");

		mockMvc.perform(multipart("/api/v1/documents/uploads/preflight")
					.file(file("first.txt", MediaType.TEXT_PLAIN_VALUE, "same content"))
					.file(file("second.txt", MediaType.TEXT_PLAIN_VALUE, "same content"))
					.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("ACCEPTED"))
				.andExpect(jsonPath("$.items[1].status").value("DUPLICATE"))
				.andExpect(jsonPath("$.items[1].confirmationToken").isNotEmpty());
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from upload_confirmation_tokens", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void sameUserDuplicateReturnsHashedConfirmationTokenBoundToFileAndCollection()
			throws Exception {
		String firstEmail = "first@example.com";
		String firstAccessToken = registerAndLogin(firstEmail);
		UUID firstUserId = userId(firstEmail);
		UUID collectionId = createCollection(firstUserId, "Research");
		byte[] content = "duplicate content".getBytes(StandardCharsets.UTF_8);
		String fileHash = "b79f8c07798dcc75d6f288e6a620644a88a9c67e74019a57b88a5bfd918e4b0f";
		insertDocument(firstUserId, collectionId, fileHash);

		MvcResult duplicateResult = mockMvc.perform(multipart("/api/v1/documents/uploads/preflight")
					.file(new MockMultipartFile(
							"files", "duplicate.txt", MediaType.TEXT_PLAIN_VALUE, content))
					.param("collectionId", collectionId.toString())
					.header("Authorization", "Bearer " + firstAccessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("DUPLICATE"))
				.andExpect(jsonPath("$.items[0].confirmationToken").isNotEmpty())
				.andReturn();

		JsonNode item = body(duplicateResult).get("items").get(0);
		String rawToken = item.get("confirmationToken").asText();
		Map<String, Object> storedToken = jdbcTemplate.queryForMap(
				"select token_hash, user_id, collection_id, file_hash, filename, size_bytes, "
						+ "expires_at, used_at from upload_confirmation_tokens");
		assertThat(storedToken.get("token_hash")).isNotEqualTo(rawToken);
		assertThat(storedToken.get("user_id")).isEqualTo(firstUserId);
		assertThat(storedToken.get("collection_id")).isEqualTo(collectionId);
		assertThat(storedToken.get("file_hash")).isEqualTo(fileHash);
		assertThat(storedToken.get("filename")).isEqualTo("duplicate.txt");
		assertThat(storedToken.get("size_bytes")).isEqualTo((long) content.length);
		assertThat(storedToken.get("used_at")).isNull();
		assertInvalidConfirmation(() -> confirmationTokenService.consume(
				rawToken,
				firstUserId,
				fileHash,
				"changed.txt",
				content.length,
				collectionId));
		UploadConfirmationTokenService.Confirmation confirmation = confirmationTokenService.consume(
				rawToken,
				firstUserId,
				fileHash,
				"duplicate.txt",
				content.length,
				collectionId);
		assertThat(confirmation.collectionId()).isEqualTo(collectionId);
		assertThat(jdbcTemplate.queryForObject(
				"select used_at is not null from upload_confirmation_tokens", Boolean.class))
				.isTrue();
		assertInvalidConfirmation(() -> confirmationTokenService.consume(
				rawToken,
				firstUserId,
				fileHash,
				"duplicate.txt",
				content.length,
				collectionId));

		String secondAccessToken = registerAndLogin("second@example.com");
		mockMvc.perform(multipart("/api/v1/documents/uploads/preflight")
					.file(new MockMultipartFile(
							"files", "duplicate.txt", MediaType.TEXT_PLAIN_VALUE, content))
					.header("Authorization", "Bearer " + secondAccessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("ACCEPTED"))
				.andExpect(jsonPath("$.items[0].confirmationToken").doesNotExist());
	}

	@Test
	void preflightRequiresAuthenticationAndOwnedCollection() throws Exception {
		MockMultipartFile file = file("notes.txt", MediaType.TEXT_PLAIN_VALUE, "notes");
		mockMvc.perform(multipart("/api/v1/documents/uploads/preflight").file(file))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

		String ownerEmail = "owner@example.com";
		registerAndLogin(ownerEmail);
		UUID foreignCollectionId = createCollection(userId(ownerEmail), "Private");
		String intruderToken = registerAndLogin("intruder@example.com");

		mockMvc.perform(multipart("/api/v1/documents/uploads/preflight")
					.file(file)
					.param("collectionId", foreignCollectionId.toString())
					.header("Authorization", "Bearer " + intruderToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void malformedAndExcessiveMultipartRequestsUseStandardErrors() throws Exception {
		String accessToken = registerAndLogin("limits@example.com");
		mockMvc.perform(multipart("/api/v1/documents/uploads/preflight")
					.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());

		mockMvc.perform(multipart("/api/v1/documents/uploads/preflight")
					.file(file("notes.txt", MediaType.TEXT_PLAIN_VALUE, "notes"))
					.param("collectionId", "not-a-uuid")
					.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

		var oversizedBatch = multipart("/api/v1/documents/uploads/preflight")
				.header("Authorization", "Bearer " + accessToken);
		for (int index = 0; index < 21; index++) {
			oversizedBatch.file(file(
					"notes-" + index + ".txt", MediaType.TEXT_PLAIN_VALUE, "note " + index));
		}
		mockMvc.perform(oversizedBatch)
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.metadata.maxFilesPerBatch").value(20));
	}

	private MockMultipartFile file(String filename, String browserMediaType, String content) {
		return new MockMultipartFile(
				"files", filename, browserMediaType, content.getBytes(StandardCharsets.UTF_8));
	}

	private String registerAndLogin(String email) throws Exception {
		String credentials = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
		mockMvc.perform(post("/api/v1/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(credentials))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(credentials))
				.andExpect(status().isOk())
				.andReturn();
		return body(login).get("accessToken").asText();
	}

	private UUID userId(String email) {
		return jdbcTemplate.queryForObject(
				"select id from users where email = ?", UUID.class, email);
	}

	private UUID createCollection(UUID userId, String name) {
		return jdbcTemplate.queryForObject(
				"insert into collections (user_id, name) values (?, ?) returning id",
				UUID.class,
				userId,
				name);
	}

	private void insertDocument(UUID userId, UUID collectionId, String hash) {
		jdbcTemplate.update(
				"insert into documents (user_id, collection_id, original_filename, object_key, "
						+ "media_type, file_extension, size_bytes, sha256_hash) "
						+ "values (?, ?, 'existing.txt', ?, 'text/plain', 'txt', 17, ?)",
				userId,
				collectionId,
				"objects/" + UUID.randomUUID(),
				hash);
	}

	private JsonNode body(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}

	private void assertInvalidConfirmation(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
		assertThatThrownBy(action)
				.isInstanceOf(ApiException.class)
				.satisfies(exception -> assertThat(((ApiException) exception).getCode())
						.isEqualTo(ErrorCode.UPLOAD_CONFIRMATION_TOKEN_INVALID));
	}
}
