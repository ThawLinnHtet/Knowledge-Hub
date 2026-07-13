package com.knowledgehub.api.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowledgehub.api.TestcontainersConfiguration;
import com.knowledgehub.api.storage.ObjectStorage;
import com.knowledgehub.api.storage.ObjectStorageException;
import com.knowledgehub.api.storage.FakeObjectStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import({
	TestcontainersConfiguration.class,
	DocumentWorkflowIntegrationTest.StorageFailureConfiguration.class
})
@AutoConfigureMockMvc
@SpringBootTest
class DocumentWorkflowIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery-staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectStorage objectStorage;

	@Autowired
	private ConfirmedUploadItemProcessor uploadItemProcessor;

	@Autowired
	private UploadValidator uploadValidator;

	@BeforeEach
	void clearUsers() {
		jdbcTemplate.update("delete from users");
	}

	@Test
	void registrationCreatesFallbackAndCollectionCrudReassignsDocuments() throws Exception {
		Session owner = registerAndLogin("collections@example.com");
		MvcResult initial = mockMvc.perform(get("/api/v1/collections")
					.header("Authorization", owner.authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Uncategorized"))
				.andExpect(jsonPath("$[0].uncategorized").value(true))
				.andExpect(jsonPath("$[0].documentCount").value(0))
				.andReturn();
		UUID fallbackId = UUID.fromString(body(initial).get(0).get("id").asText());

		MvcResult created = mockMvc.perform(post("/api/v1/collections")
					.header("Authorization", owner.authorization())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("name", "Research"))))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.name").value("Research"))
				.andReturn();
		UUID researchId = UUID.fromString(body(created).get("id").asText());

		mockMvc.perform(post("/api/v1/collections")
					.header("Authorization", owner.authorization())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("name", " research "))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COLLECTION_NAME_UNAVAILABLE"));

		mockMvc.perform(patch("/api/v1/collections/{id}", researchId)
					.header("Authorization", owner.authorization())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("name", "Sources"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Sources"));

		UUID documentId = insertDocument(owner.userId(), researchId, "collection.txt", "objects/move");
		mockMvc.perform(delete("/api/v1/collections/{id}", researchId)
					.header("Authorization", owner.authorization()))
				.andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject(
				"select collection_id from documents where id = ?", UUID.class, documentId))
				.isEqualTo(fallbackId);

		mockMvc.perform(delete("/api/v1/collections/{id}", fallbackId)
					.header("Authorization", owner.authorization()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("UNCATEGORIZED_COLLECTION_PROTECTED"));

		Session other = registerAndLogin("other-collections@example.com");
		mockMvc.perform(patch("/api/v1/collections/{id}", fallbackId)
					.header("Authorization", other.authorization())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("name", "Mine"))))
				.andExpect(status().isNotFound());
		upload(
					other,
					List.of(file("foreign.txt", "foreign")),
					fallbackId,
					List.of(decision(0, "UPLOAD", null)))
				.andExpect(status().isNotFound());
	}

	@Test
	void confirmedUploadSupportsPartialSuccessAndDuplicateConfirmation() throws Exception {
		Session session = registerAndLogin("uploads@example.com");
		MockMultipartFile valid = file("notes.txt", "knowledge hub notes");
		MockMultipartFile storageFailure = file("failure.txt", "fail storage");
		MockMultipartFile invalid = file("program.exe", "not allowed");

		MvcResult partial = upload(
				 session,
				 List.of(valid, storageFailure, invalid),
				 null,
				 List.of(
						 decision(0, "UPLOAD", null),
						 decision(1, "UPLOAD", null),
						 decision(2, "UPLOAD", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("UPLOADED"))
				.andExpect(jsonPath("$.items[0].document.status").value("PENDING"))
				.andExpect(jsonPath("$.items[0].document.collection.name").value("Uncategorized"))
				.andExpect(jsonPath("$.items[1].status").value("REJECTED"))
				.andExpect(jsonPath("$.items[1].error.code").value("STORAGE_ERROR"))
				.andExpect(jsonPath("$.items[2].status").value("REJECTED"))
				.andExpect(jsonPath("$.items[2].error.code").value("VALIDATION_FAILED"))
				.andReturn();
		UUID firstDocumentId = UUID.fromString(
				body(partial).get("items").get(0).get("document").get("id").asText());
		String objectKey = jdbcTemplate.queryForObject(
				"select object_key from documents where id = ?", String.class, firstDocumentId);
		assertThat(objectKey).doesNotContain("notes.txt");
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from documents where user_id = ?", Integer.class, session.userId()))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from storage_cleanup_jobs", Integer.class))
				.isZero();

		upload(session, List.of(file("notes.txt", "knowledge hub notes")), null,
					List.of(decision(0, "UPLOAD", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("REJECTED"))
				.andExpect(jsonPath("$.items[0].error.code").value("DUPLICATE_UPLOAD"));

		MvcResult preflight = mockMvc.perform(multipart("/api/v1/documents/uploads/preflight")
					.file(file("notes.txt", "knowledge hub notes"))
					.header("Authorization", session.authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("DUPLICATE"))
				.andReturn();
		String confirmationToken =
				body(preflight).get("items").get(0).get("confirmationToken").asText();

		upload(session, List.of(file("notes.txt", "knowledge hub notes")), null,
					List.of(decision(0, "UPLOAD_DUPLICATE", confirmationToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("UPLOADED"));
		upload(session, List.of(file("notes.txt", "knowledge hub notes")), null,
					List.of(decision(0, "UPLOAD_DUPLICATE", confirmationToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("REJECTED"))
				.andExpect(jsonPath("$.items[0].error.code")
						.value("UPLOAD_CONFIRMATION_TOKEN_INVALID"));
	}

	@Test
	void concurrentSameHashUploadsRequireExplicitDuplicateConsent() throws Exception {
		Session owner = registerAndLogin("concurrent-upload@example.com");
		UUID fallbackId = jdbcTemplate.queryForObject(
				"select id from collections where user_id = ? and uncategorized",
				UUID.class,
				owner.userId());
		MockMultipartFile firstFile = file("first.txt", "concurrent content");
		MockMultipartFile secondFile = file("second.txt", "concurrent content");
		var firstUpload = uploadValidator.validate(firstFile);
		var secondUpload = uploadValidator.validate(secondFile);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var firstResult = executor.submit(() -> concurrentUpload(
					start, owner.userId(), fallbackId, firstFile, firstUpload, "documents/first"));
			var secondResult = executor.submit(() -> concurrentUpload(
					start, owner.userId(), fallbackId, secondFile, secondUpload, "documents/second"));
			start.countDown();
			assertThat(List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS))
					.stream()
					.filter(Boolean::booleanValue)
					.count())
				.isEqualTo(1);
		}
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from documents where user_id = ?", Integer.class, owner.userId()))
				.isEqualTo(1);
	}

	@Test
	void documentLibraryIsOwnerScopedAndSupportsDownloadRetryAndDeletion() throws Exception {
		Session owner = registerAndLogin("library@example.com");
		MvcResult uploaded = upload(
				 owner,
				 List.of(file("manual.md", "# Manual")),
				 null,
				 List.of(decision(0, "UPLOAD", null)))
				.andExpect(status().isOk())
				.andReturn();
		UUID documentId = UUID.fromString(
				body(uploaded).get("items").get(0).get("document").get("id").asText());
		String objectKey = jdbcTemplate.queryForObject(
				"select object_key from documents where id = ?", String.class, documentId);
		UUID chunkId = insertChunk(documentId);
		UUID citationId = insertCitation(owner.userId(), documentId, chunkId);

		mockMvc.perform(get("/api/v1/documents")
					.param("status", "PENDING")
					.param("fileExtension", "md")
					.header("Authorization", owner.authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].filename").value("manual.md"))
				.andExpect(jsonPath("$.items[0].objectKey").doesNotExist());
		mockMvc.perform(get("/api/v1/documents")
					.header("Authorization", owner.authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/api/v1/documents/{id}", documentId)
					.header("Authorization", owner.authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.filename").value("manual.md"))
				.andExpect(jsonPath("$.chunks.length()").value(1))
				.andExpect(jsonPath("$.chunks[0].snippet").value("Manual chunk"))
				.andExpect(jsonPath("$.citations.length()").value(1));

		mockMvc.perform(post("/api/v1/documents/{id}/download-url", documentId)
					.header("Authorization", owner.authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("fake-storage")))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty());

		jdbcTemplate.update(
				"update documents set status = 'FAILED', failure_code = 'TEMPORARY', "
						+ "failure_message = 'Try again', retryable = true, retry_count = 2, "
						+ "next_retry_at = now() where id = ?",
				documentId);
		mockMvc.perform(post("/api/v1/documents/{id}/retry", documentId)
					.header("Authorization", owner.authorization()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.failureCode").doesNotExist());

		Session intruder = registerAndLogin("intruder-library@example.com");
		mockMvc.perform(get("/api/v1/documents/{id}", documentId)
					.header("Authorization", intruder.authorization()))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/documents/{id}/download-url", documentId)
					.header("Authorization", intruder.authorization()))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/documents/{id}", documentId)
					.header("Authorization", intruder.authorization()))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/v1/documents/{id}", documentId)
					.header("Authorization", owner.authorization()))
				.andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from documents where id = ?", Integer.class, documentId))
				.isZero();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from document_chunks where document_id = ?", Integer.class, documentId))
				.isZero();
		Map<String, Object> citation = jdbcTemplate.queryForMap(
				"select document_id, chunk_id, source_deleted from message_citations where id = ?",
				citationId);
		assertThat(citation.get("document_id")).isNull();
		assertThat(citation.get("chunk_id")).isNull();
		assertThat(citation.get("source_deleted")).isEqualTo(true);
		assertThatThrownBy(() -> objectStorage.createDownloadUrl(
						owner.userId(), objectKey, Duration.ofMinutes(5)))
				.isInstanceOf(ObjectStorageException.class);

		MvcResult missingObjectUpload = upload(
				 owner,
				 List.of(file("missing.txt", "missing object")),
				 null,
				 List.of(decision(0, "UPLOAD", null)))
				.andExpect(status().isOk())
				.andReturn();
		UUID missingObjectDocumentId = UUID.fromString(body(missingObjectUpload)
				.get("items")
				.get(0)
				.get("document")
				.get("id")
				.asText());
		String missingObjectKey = jdbcTemplate.queryForObject(
				"select object_key from documents where id = ?",
				String.class,
				missingObjectDocumentId);
		objectStorage.delete(owner.userId(), missingObjectKey);
		mockMvc.perform(delete("/api/v1/documents/{id}", missingObjectDocumentId)
					.header("Authorization", owner.authorization()))
				.andExpect(status().isNoContent());
	}

	private org.springframework.test.web.servlet.ResultActions upload(
			Session session,
			List<MockMultipartFile> files,
			UUID collectionId,
			List<Map<String, Object>> decisions)
			throws Exception {
		var request = multipart("/api/v1/documents/uploads")
				.file(new MockMultipartFile(
						"manifest",
						"",
						MediaType.APPLICATION_JSON_VALUE,
						objectMapper
								.writeValueAsBytes(Map.of("items", decisions))))
				.header("Authorization", session.authorization());
		files.forEach(request::file);
		if (collectionId != null) {
			request.param("collectionId", collectionId.toString());
		}
		return mockMvc.perform(request);
	}

	private Map<String, Object> decision(int fileIndex, String decision, String token) {
		return token == null
				? Map.of("fileIndex", fileIndex, "decision", decision)
				: Map.of("fileIndex", fileIndex, "decision", decision, "confirmationToken", token);
	}

	private MockMultipartFile file(String filename, String content) {
		return new MockMultipartFile(
				"files", filename, MediaType.TEXT_PLAIN_VALUE, content.getBytes(StandardCharsets.UTF_8));
	}

	private Session registerAndLogin(String email) throws Exception {
		String credentials = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
		MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(credentials))
				.andExpect(status().isCreated())
				.andReturn();
		UUID userId = UUID.fromString(body(registered).get("id").asText());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(credentials))
				.andExpect(status().isOk())
				.andReturn();
		return new Session(userId, body(login).get("accessToken").asText());
	}

	private UUID insertDocument(UUID userId, UUID collectionId, String filename, String objectKey) {
		return jdbcTemplate.queryForObject(
				"insert into documents (user_id, collection_id, original_filename, object_key, "
						+ "media_type, file_extension, size_bytes, sha256_hash) "
						+ "values (?, ?, ?, ?, 'text/plain', 'txt', 4, ?) returning id",
				UUID.class,
				userId,
				collectionId,
				filename,
				objectKey,
				"a".repeat(64));
	}

	private UUID insertChunk(UUID documentId) {
		String vector = "[" + "0,".repeat(1023) + "0]";
		return jdbcTemplate.queryForObject(
				"insert into document_chunks (document_id, chunk_order, content, character_count, "
						+ "embedding_model, embedding_dimension, embedding) "
						+ "values (?, 0, 'Manual chunk', 12, 'fake', 1024, cast(? as vector)) returning id",
				UUID.class,
				documentId,
				vector);
	}

	private UUID insertCitation(UUID userId, UUID documentId, UUID chunkId) {
		UUID chatId = jdbcTemplate.queryForObject(
				"insert into chat_sessions (user_id, title) values (?, 'Sources') returning id",
				UUID.class,
				userId);
		UUID messageId = jdbcTemplate.queryForObject(
				"insert into chat_messages (chat_session_id, role, content) "
						+ "values (?, 'ASSISTANT', 'Answer') returning id",
				UUID.class,
				chatId);
		return jdbcTemplate.queryForObject(
				"insert into message_citations (message_id, document_id, chunk_id, source_title, "
						+ "relevance_score, citation_order) values (?, ?, ?, 'manual.md', 0.9, 1) returning id",
				UUID.class,
				messageId,
				documentId,
				chunkId);
	}

	private JsonNode body(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}

	private boolean concurrentUpload(
			CountDownLatch start,
			UUID userId,
			UUID fallbackId,
			MockMultipartFile file,
			UploadValidator.ValidatedUpload upload,
			String objectKey)
			throws Exception {
		start.await(10, TimeUnit.SECONDS);
		try {
			uploadItemProcessor.upload(
					userId,
					fallbackId,
					null,
					file,
					upload,
					DocumentUploadController.UploadDecisionType.UPLOAD,
					null,
					objectKey);
			return true;
		} catch (com.knowledgehub.api.common.ApiException exception) {
			assertThat(exception.getCode()).isEqualTo(com.knowledgehub.api.common.ErrorCode.DUPLICATE_UPLOAD);
			return false;
		}
	}

	private record Session(UUID userId, String accessToken) {

		String authorization() {
			return "Bearer " + accessToken;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StorageFailureConfiguration {

		@Bean
		@Primary
		ObjectStorage controlledObjectStorage() {
			return new ControlledObjectStorage();
		}
	}

	static class ControlledObjectStorage implements ObjectStorage {

		private final FakeObjectStorage delegate = new FakeObjectStorage();

		@Override
		public void put(
				UUID userId, String objectKey, InputStream content, long size, String mediaType) {
			try {
				byte[] bytes = content.readAllBytes();
				if (new String(bytes, StandardCharsets.UTF_8).equals("fail storage")) {
					throw new ObjectStorageException("Simulated storage failure.");
				}
				delegate.put(
						userId, objectKey, new ByteArrayInputStream(bytes), size, mediaType);
			} catch (IOException exception) {
				throw new ObjectStorageException("Could not read test content.", exception);
			}
		}

		@Override
		public InputStream get(UUID userId, String objectKey) {
			return delegate.get(userId, objectKey);
		}

		@Override
		public URI createDownloadUrl(UUID userId, String objectKey, Duration ttl) {
			return delegate.createDownloadUrl(userId, objectKey, ttl);
		}

		@Override
		public void delete(UUID userId, String objectKey) {
			delegate.delete(userId, objectKey);
		}

		@Override
		public void deleteAll(UUID userId) {
			delegate.deleteAll(userId);
		}
	}
}
