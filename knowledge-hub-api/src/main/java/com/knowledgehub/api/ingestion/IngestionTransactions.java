package com.knowledgehub.api.ingestion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngestionTransactions {

	private final JdbcTemplate jdbcTemplate;
	private final IngestionProperties properties;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<ClaimedDocument> claimNext(Instant now) {
		jdbcTemplate.update(
				"update documents set status = 'FAILED', failure_code = 'PROCESSING_FAILED', "
						+ "failure_message = 'The ingestion lease expired after all retries.', "
						+ "retryable = true, next_retry_at = null, processing_lock_id = null, "
						+ "processing_lock_expires_at = null, updated_at = ? "
						+ "where status = 'PROCESSING' and processing_lock_expires_at < ? "
						+ "and retry_count >= ?",
				timestamp(now),
				timestamp(now),
				properties.maxRetries());
		UUID lockId = UUID.randomUUID();
		String sql = "with candidate as ("
				+ "select id, status from documents where "
				+ "(status = 'PENDING' and (next_retry_at is null or next_retry_at <= ?)) or "
				+ "(status = 'FAILED' and retryable and next_retry_at <= ?) or "
				+ "(status = 'PROCESSING' and processing_lock_expires_at < ? "
				+ "and retry_count < ?) "
				+ "order by created_at for update skip locked limit 1) "
				+ "update documents document set status = 'PROCESSING', processing_lock_id = ?, "
				+ "processing_lock_expires_at = ?, processing_started_at = ?, updated_at = ?, "
				+ "retry_count = case when candidate.status = 'PROCESSING' "
				+ "then document.retry_count + 1 else document.retry_count end "
				+ "from candidate where document.id = candidate.id "
				+ "returning document.id, document.user_id, document.object_key, "
				+ "document.original_filename, document.media_type";
		return jdbcTemplate.query(
				sql,
				result -> result.next() ? Optional.of(mapClaim(result, lockId)) : Optional.empty(),
				timestamp(now),
				timestamp(now),
				timestamp(now),
				properties.maxRetries(),
				lockId,
				timestamp(now.plus(properties.leaseDuration())),
				timestamp(now),
				timestamp(now));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void complete(
			ClaimedDocument document,
			List<EmbeddedChunk> chunks,
			String embeddingModel,
			int embeddingDimension,
			Instant now) {
		List<UUID> owned = jdbcTemplate.query(
				"select id from documents where id = ? and status = 'PROCESSING' "
						+ "and processing_lock_id = ? for update",
				(result, row) -> result.getObject(1, UUID.class),
				document.id(),
				document.lockId());
		if (owned.isEmpty()) {
			return;
		}
		jdbcTemplate.update("delete from document_chunks where document_id = ?", document.id());
		for (int order = 0; order < chunks.size(); order++) {
			EmbeddedChunk chunk = chunks.get(order);
			jdbcTemplate.update(
					"insert into document_chunks (document_id, chunk_order, content, "
							+ "start_position, end_position, character_count, token_estimate, "
							+ "embedding_model, embedding_dimension, embedding) "
							+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as vector))",
					document.id(),
					order,
					chunk.chunk().content(),
					chunk.chunk().startPosition(),
					chunk.chunk().endPosition(),
					chunk.chunk().characterCount(),
					chunk.chunk().tokenEstimate(),
					embeddingModel,
					embeddingDimension,
					vectorLiteral(chunk.embedding()));
		}
		jdbcTemplate.update(
				"update documents set status = 'READY', failure_code = null, "
						+ "failure_message = null, retryable = false, next_retry_at = null, "
						+ "processing_lock_id = null, processing_lock_expires_at = null, "
						+ "processed_at = ?, updated_at = ? where id = ? and processing_lock_id = ?",
				timestamp(now),
				timestamp(now),
				document.id(),
				document.lockId());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fail(
			ClaimedDocument document,
			String code,
			String message,
			boolean transientFailure,
			Instant now) {
		List<Integer> retries = jdbcTemplate.query(
				"select retry_count from documents where id = ? and status = 'PROCESSING' "
						+ "and processing_lock_id = ? for update",
				(result, row) -> result.getInt(1),
				document.id(),
				document.lockId());
		if (retries.isEmpty()) {
			return;
		}
		int retryCount = transientFailure ? retries.getFirst() + 1 : retries.getFirst();
		boolean retryable = transientFailure;
		Instant nextRetryAt = transientFailure && retryCount <= properties.maxRetries()
				? now.plus(properties.retryDelay())
				: null;
		jdbcTemplate.update(
				"update documents set status = 'FAILED', failure_code = ?, failure_message = ?, "
						+ "retry_count = ?, retryable = ?, next_retry_at = ?, "
						+ "processing_lock_id = null, processing_lock_expires_at = null, updated_at = ? "
						+ "where id = ? and processing_lock_id = ?",
				code,
				safeMessage(message),
				retryCount,
				retryable,
				nextRetryAt == null ? null : timestamp(nextRetryAt),
				timestamp(now),
				document.id(),
				document.lockId());
	}

	private ClaimedDocument mapClaim(ResultSet result, UUID lockId) throws SQLException {
		return new ClaimedDocument(
				result.getObject("id", UUID.class),
				result.getObject("user_id", UUID.class),
				result.getString("object_key"),
				result.getString("original_filename"),
				result.getString("media_type"),
				lockId);
	}

	private String vectorLiteral(float[] embedding) {
		StringBuilder value = new StringBuilder(embedding.length * 12).append('[');
		for (int index = 0; index < embedding.length; index++) {
			if (index > 0) {
				value.append(',');
			}
			value.append(embedding[index]);
		}
		return value.append(']').toString();
	}

	private String safeMessage(String message) {
		if (message == null || message.isBlank()) {
			return "Document processing failed.";
		}
		return message.substring(0, Math.min(message.length(), 1000));
	}

	private Timestamp timestamp(Instant instant) {
		return Timestamp.from(instant);
	}

	public record ClaimedDocument(
			UUID id,
			UUID userId,
			String objectKey,
			String filename,
			String mediaType,
			UUID lockId) {}

	public record EmbeddedChunk(DocumentChunker.Chunk chunk, float[] embedding) {}
}
