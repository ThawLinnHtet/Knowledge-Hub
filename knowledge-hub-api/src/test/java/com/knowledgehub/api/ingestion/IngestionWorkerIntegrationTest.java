package com.knowledgehub.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledgehub.api.TestcontainersConfiguration;
import com.knowledgehub.api.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
	"app.ingestion.max-extracted-characters=20",
	"app.ingestion.max-chunks=5",
	"app.ingestion.chunk-size=10",
	"app.ingestion.chunk-overlap=2",
	"app.ingestion.retry-delay=1m"
})
class IngestionWorkerIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectStorage objectStorage;

	@Autowired
	private IngestionWorker worker;

	@BeforeEach
	void clearUsers() {
		jdbcTemplate.update("delete from users");
	}

	@Test
	void processesPendingDocumentToReadyWithSearchableEmbeddedChunks() {
		UUID documentId = pendingDocument("notes.txt", "knowledge hub text");

		worker.processNext();

		Map<String, Object> document = jdbcTemplate.queryForMap(
				"select status, processed_at, processing_lock_id from documents where id = ?",
				documentId);
		assertThat(document.get("status")).isEqualTo("READY");
		assertThat(document.get("processed_at")).isNotNull();
		assertThat(document.get("processing_lock_id")).isNull();
		assertThat(jdbcTemplate.queryForList(
				"select chunk_order, content, embedding_model, embedding_dimension, "
						+ "search_vector::text as search_text from document_chunks "
						+ "where document_id = ? order by chunk_order",
				documentId))
				.hasSize(2)
				.allSatisfy(chunk -> {
					assertThat(chunk.get("embedding_model")).isEqualTo("fake-embedding");
					assertThat(chunk.get("embedding_dimension")).isEqualTo(1024);
					assertThat(chunk.get("search_text")).isNotNull();
				});
	}

	@Test
	void storageFailureSchedulesAVisibleTransientRetry() {
		UUID documentId = pendingDocumentWithoutObject("missing.txt", 7);

		worker.processNext();

		Map<String, Object> document = jdbcTemplate.queryForMap(
				"select status, failure_code, retry_count, retryable, next_retry_at, "
						+ "processing_lock_id from documents where id = ?",
				documentId);
		assertThat(document.get("status")).isEqualTo("FAILED");
		assertThat(document.get("failure_code")).isEqualTo("STORAGE_ERROR");
		assertThat(document.get("retry_count")).isEqualTo(1);
		assertThat(document.get("retryable")).isEqualTo(true);
		assertThat(document.get("next_retry_at")).isNotNull();
		assertThat(document.get("processing_lock_id")).isNull();

		Map<String, Object> stored = jdbcTemplate.queryForMap(
				"select user_id, object_key from documents where id = ?", documentId);
		byte[] recovered = "recovered".getBytes(StandardCharsets.UTF_8);
		objectStorage.put(
				(UUID) stored.get("user_id"),
				(String) stored.get("object_key"),
				new ByteArrayInputStream(recovered),
				recovered.length,
				"text/plain");
		jdbcTemplate.update(
				"update documents set next_retry_at = now() - interval '1 second' where id = ?",
				documentId);

		worker.processNext();

		assertThat(jdbcTemplate.queryForMap(
				"select status, retry_count, failure_code, next_retry_at from documents where id = ?",
				documentId))
				.containsEntry("status", "READY")
				.containsEntry("retry_count", 1)
				.containsEntry("failure_code", null)
				.containsEntry("next_retry_at", null);
	}

	@Test
	void extractedCharacterLimitFailsPermanentlyBeforeEmbedding() {
		UUID documentId = pendingDocument("large.txt", "123456789012345678901");

		worker.processNext();

		Map<String, Object> document = jdbcTemplate.queryForMap(
				"select status, failure_code, retry_count, retryable, next_retry_at "
						+ "from documents where id = ?",
				documentId);
		assertThat(document.get("status")).isEqualTo("FAILED");
		assertThat(document.get("failure_code")).isEqualTo("LIMIT_EXCEEDED");
		assertThat(document.get("retry_count")).isEqualTo(0);
		assertThat(document.get("retryable")).isEqualTo(false);
		assertThat(document.get("next_retry_at")).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from document_chunks where document_id = ?",
				Integer.class,
				documentId))
				.isZero();
	}

	@Test
	void expiredLeaseStopsAfterTheConfiguredRetryCap() {
		UUID documentId = pendingDocumentWithoutObject("stalled.txt", 7);
		jdbcTemplate.update(
				"update documents set status = 'PROCESSING', retry_count = 5, "
						+ "processing_lock_id = ?, processing_lock_expires_at = now() - interval '1 minute' "
						+ "where id = ?",
				UUID.randomUUID(),
				documentId);

		worker.processNext();

		assertThat(jdbcTemplate.queryForMap(
				"select status, failure_code, retry_count, retryable, next_retry_at, "
						+ "processing_lock_id from documents where id = ?",
				documentId))
				.containsEntry("status", "FAILED")
				.containsEntry("failure_code", "PROCESSING_FAILED")
				.containsEntry("retry_count", 5)
				.containsEntry("retryable", true)
				.containsEntry("next_retry_at", null)
				.containsEntry("processing_lock_id", null);
	}

	private UUID pendingDocument(String filename, String content) {
		byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
		UUID userId = insertUser();
		UUID collectionId = insertCollection(userId);
		String objectKey = "documents/" + UUID.randomUUID();
		objectStorage.put(
				userId,
				objectKey,
				new ByteArrayInputStream(bytes),
				bytes.length,
				"text/plain");
		return insertDocument(userId, collectionId, filename, objectKey, bytes.length);
	}

	private UUID pendingDocumentWithoutObject(String filename, long size) {
		UUID userId = insertUser();
		UUID collectionId = insertCollection(userId);
		return insertDocument(
				userId, collectionId, filename, "documents/" + UUID.randomUUID(), size);
	}

	private UUID insertUser() {
		return jdbcTemplate.queryForObject(
				"insert into users (email, password_hash) values (?, 'hash') returning id",
				UUID.class,
				UUID.randomUUID() + "@example.com");
	}

	private UUID insertCollection(UUID userId) {
		return jdbcTemplate.queryForObject(
				"insert into collections (user_id, name, uncategorized) "
						+ "values (?, 'Uncategorized', true) returning id",
				UUID.class,
				userId);
	}

	private UUID insertDocument(
			UUID userId, UUID collectionId, String filename, String objectKey, long size) {
		return jdbcTemplate.queryForObject(
				"insert into documents (user_id, collection_id, original_filename, object_key, "
						+ "media_type, file_extension, size_bytes, sha256_hash) "
						+ "values (?, ?, ?, ?, 'text/plain', 'txt', ?, ?) returning id",
				UUID.class,
				userId,
				collectionId,
				filename,
				objectKey,
				size,
				"a".repeat(64));
	}
}
