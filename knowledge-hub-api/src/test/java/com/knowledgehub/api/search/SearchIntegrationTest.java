package com.knowledgehub.api.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowledgehub.api.TestcontainersConfiguration;
import com.knowledgehub.api.ingestion.DocumentEmbeddingClient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class SearchIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery-staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DocumentEmbeddingClient embeddingClient;

	@BeforeEach
	void clearUsers() {
		jdbcTemplate.update("delete from users");
	}

	@Test
	void supportsKeywordSemanticAndHybridRanking() throws Exception {
		Session owner = registerAndLogin("search@example.com");
		UUID collectionId = fallbackCollection(owner.userId());
		float[] queryVector = embeddingClient.embed("alpha project");
		float[] opposite = negate(queryVector);
		insertChunk(insertDocument(owner.userId(), collectionId, "keyword.md", "READY", "md"),
				"The alpha project launch checklist", opposite);
		insertChunk(insertDocument(owner.userId(), collectionId, "semantic.txt", "READY", "txt"),
				"Unrelated wording", queryVector);
		UUID bothDocument = insertDocument(
				owner.userId(), collectionId, "both.txt", "READY", "txt");
		insertChunk(bothDocument, "Alpha project architecture", queryVector);

		mockMvc.perform(get("/api/v1/search")
					.header("Authorization", owner.authorization())
					.param("q", "alpha project")
					.param("mode", "KEYWORD"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("KEYWORD"))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].keywordScore").isNumber());

		mockMvc.perform(get("/api/v1/search")
					.header("Authorization", owner.authorization())
					.param("q", "alpha project")
					.param("mode", "SEMANTIC"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].semanticScore").value(1.0))
				.andExpect(jsonPath("$.items[0].filename").value(
						org.hamcrest.Matchers.oneOf("semantic.txt", "both.txt")));

		mockMvc.perform(get("/api/v1/search")
					.header("Authorization", owner.authorization())
					.param("q", "alpha project")
					.param("mode", "HYBRID"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].documentId").value(bothDocument.toString()))
				.andExpect(jsonPath("$.items[0].matchType").value("BOTH"))
				.andExpect(jsonPath("$.items[0].snippet").isNotEmpty())
				.andExpect(jsonPath("$.items[0].startPosition").value(0));
	}

	@Test
	void enforcesOwnershipReadyStatusAndAllFilters() throws Exception {
		Session owner = registerAndLogin("filters@example.com");
		Session intruder = registerAndLogin("foreign-search@example.com");
		UUID selectedCollection = insertCollection(owner.userId(), "Selected");
		UUID otherCollection = insertCollection(owner.userId(), "Other");
		float[] vector = embeddingClient.embed("filter target");
		UUID expected = insertDocument(
				owner.userId(), selectedCollection, "target.md", "READY", "md");
		insertChunk(expected, "filter target", vector);
		insertChunk(
				insertDocument(owner.userId(), otherCollection, "other.md", "READY", "md"),
				"filter target",
				vector);
		insertChunk(
				insertDocument(owner.userId(), selectedCollection, "pending.md", "PENDING", "md"),
				"filter target",
				vector);
		insertChunk(
				insertDocument(
						intruder.userId(), fallbackCollection(intruder.userId()), "foreign.md", "READY", "md"),
				"filter target",
				vector);

		mockMvc.perform(get("/api/v1/search")
					.header("Authorization", owner.authorization())
					.param("q", "filter target"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[*].filename").value(
						org.hamcrest.Matchers.containsInAnyOrder("target.md", "other.md")));

		mockMvc.perform(get("/api/v1/search")
					.header("Authorization", owner.authorization())
					.param("q", "filter target")
					.param("collectionId", selectedCollection.toString())
					.param("documentId", expected.toString())
					.param("fileExtension", ".MD")
					.param("uploadedFrom", Instant.now().minusSeconds(3600).toString())
					.param("uploadedTo", Instant.now().plusSeconds(3600).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].documentId").value(expected.toString()))
				.andExpect(jsonPath("$.items[0].collection.id").value(selectedCollection.toString()))
				.andExpect(jsonPath("$.items[0].embedding").doesNotExist());
	}

	@Test
	void validatesQueriesAndRequiresAuthentication() throws Exception {
		Session owner = registerAndLogin("validation-search@example.com");

		mockMvc.perform(get("/api/v1/search").param("q", "anything"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mockMvc.perform(get("/api/v1/search")
					.header("Authorization", owner.authorization()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/api/v1/search")
					.header("Authorization", owner.authorization())
					.param("q", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/api/v1/search")
					.header("Authorization", owner.authorization())
					.param("q", "anything")
					.param("uploadedFrom", "2026-07-12T00:00:00Z")
					.param("uploadedTo", "2026-07-11T00:00:00Z"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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

	private UUID fallbackCollection(UUID userId) {
		return jdbcTemplate.queryForObject(
				"select id from collections where user_id = ? and uncategorized", UUID.class, userId);
	}

	private UUID insertCollection(UUID userId, String name) {
		return jdbcTemplate.queryForObject(
				"insert into collections (user_id, name) values (?, ?) returning id",
				UUID.class,
				userId,
				name);
	}

	private UUID insertDocument(
			UUID userId, UUID collectionId, String filename, String status, String extension) {
		return jdbcTemplate.queryForObject(
				"insert into documents (user_id, collection_id, original_filename, object_key, "
						+ "media_type, file_extension, size_bytes, sha256_hash, status) "
						+ "values (?, ?, ?, ?, 'text/plain', ?, 10, ?, ?) returning id",
				UUID.class,
				userId,
				collectionId,
				filename,
				"documents/" + UUID.randomUUID(),
				extension,
				UUID.randomUUID().toString().replace("-", "").repeat(2),
				status);
	}

	private void insertChunk(UUID documentId, String content, float[] embedding) {
		jdbcTemplate.update(
				"insert into document_chunks (document_id, chunk_order, content, start_position, "
						+ "end_position, character_count, token_estimate, embedding_model, "
						+ "embedding_dimension, embedding) values (?, 0, ?, 0, ?, ?, ?, ?, 1024, cast(? as vector))",
				documentId,
				content,
				content.length(),
				content.length(),
				Math.max(1, content.length() / 4),
				embeddingClient.model(),
				vector(embedding));
	}

	private float[] negate(float[] vector) {
		float[] opposite = vector.clone();
		for (int index = 0; index < opposite.length; index++) {
			opposite[index] = -opposite[index];
		}
		return opposite;
	}

	private String vector(float[] embedding) {
		StringBuilder value = new StringBuilder("[");
		for (int index = 0; index < embedding.length; index++) {
			if (index > 0) {
				value.append(',');
			}
			value.append(embedding[index]);
		}
		return value.append(']').toString();
	}

	private JsonNode body(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}

	private record Session(UUID userId, String accessToken) {
		String authorization() {
			return "Bearer " + accessToken;
		}
	}
}
