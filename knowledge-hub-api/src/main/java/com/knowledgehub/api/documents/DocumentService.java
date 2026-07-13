package com.knowledgehub.api.documents;

import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.storage.ObjectStorage;
import com.knowledgehub.api.storage.ObjectStorageException;
import com.knowledgehub.api.storage.StorageProperties;
import com.knowledgehub.api.storage.StorageCleanupService;
import com.knowledgehub.api.users.UserEntity;
import com.knowledgehub.api.users.UserRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class DocumentService {
	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

	private final UserRepository userRepository;
	private final DocumentRepository documentRepository;
	private final DocumentMapper documentMapper;
	private final ObjectStorage objectStorage;
	private final StorageProperties storageProperties;
	private final JdbcTemplate jdbcTemplate;
	private final StorageCleanupService storageCleanupService;

	@Transactional(readOnly = true)
	public DocumentDtos.DocumentPage list(
			String email,
			int page,
			int size,
			DocumentEntity.Status status,
			UUID collectionId,
			String fileExtension,
			Instant uploadedFrom,
			Instant uploadedTo) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ApiException(
					ErrorCode.VALIDATION_FAILED,
					HttpStatus.BAD_REQUEST,
					"Page must be non-negative and size must be between 1 and 100.");
		}
		if (uploadedFrom != null && uploadedTo != null && uploadedFrom.isAfter(uploadedTo)) {
			throw new ApiException(
					ErrorCode.VALIDATION_FAILED,
					HttpStatus.BAD_REQUEST,
					"The upload date range is invalid.");
		}
		UUID userId = requireUser(email).getId();
		String normalizedExtension = fileExtension == null || fileExtension.isBlank()
				? null
				: fileExtension.trim().toLowerCase(Locale.ROOT);
		var documents = documentRepository.findOwned(
				userId,
				status,
				collectionId,
				normalizedExtension,
				uploadedFrom,
				uploadedTo,
				PageRequest.of(
						page,
						size,
						Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		return new DocumentDtos.DocumentPage(
				documents.getContent().stream().map(documentMapper::toResponse).toList(),
				documents.getNumber(),
				documents.getSize(),
				documents.getTotalElements(),
				documents.getTotalPages());
	}

	@Transactional(readOnly = true)
	public DocumentDtos.DocumentDetailResponse detail(String email, UUID documentId) {
		UUID userId = requireUser(email).getId();
		DocumentEntity document = requireOwned(documentId, userId);
		return DocumentDtos.DocumentDetailResponse.from(
				documentMapper.toResponse(document),
				chunks(documentId),
				citations(documentId, userId));
	}

	public DocumentDtos.DownloadUrlResponse downloadUrl(String email, UUID documentId) {
		UUID userId = requireUser(email).getId();
		DocumentEntity document = requireOwned(documentId, userId);
		Instant expiresAt = Instant.now().plus(storageProperties.downloadUrlTtl());
		try {
			return new DocumentDtos.DownloadUrlResponse(
					objectStorage.createDownloadUrl(
							userId, document.getObjectKey(), storageProperties.downloadUrlTtl()),
					expiresAt);
		} catch (ObjectStorageException exception) {
			throw new ApiException(
					ErrorCode.STORAGE_ERROR,
					HttpStatus.SERVICE_UNAVAILABLE,
					"The original document is not available.");
		}
	}

	@Transactional
	public DocumentDtos.DocumentResponse retry(String email, UUID documentId) {
		UUID userId = requireUser(email).getId();
		DocumentEntity document = requireOwnedWithLock(documentId, userId);
		if (document.getStatus() != DocumentEntity.Status.FAILED || !document.isRetryable()) {
			throw new ApiException(
					ErrorCode.DOCUMENT_NOT_RETRYABLE,
					HttpStatus.CONFLICT,
					"The document cannot be retried.");
		}
		document.setStatus(DocumentEntity.Status.PENDING);
		document.setFailureCode(null);
		document.setFailureMessage(null);
		document.setRetryCount(0);
		document.setRetryable(false);
		document.setNextRetryAt(null);
		document.setProcessingLockId(null);
		document.setProcessingLockExpiresAt(null);
		document.setProcessingStartedAt(null);
		document.setProcessedAt(null);
		document.setUpdatedAt(Instant.now());
		return documentMapper.toResponse(document);
	}

	@Transactional
	public void delete(String email, UUID documentId) {
		UUID userId = requireUser(email).getId();
		DocumentEntity document = requireOwnedWithLock(documentId, userId);
		UUID cleanupJobId = storageCleanupService.schedule(
				userId, document.getObjectKey(), Duration.ofMinutes(1));
		jdbcTemplate.update(
				"update message_citations set source_deleted = true, updated_at = now() "
						+ "where document_id = ?",
				documentId);
		documentRepository.delete(document);
		documentRepository.flush();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					storageCleanupService.processJob(cleanupJobId);
				} catch (RuntimeException exception) {
					log.warn(
							"Could not immediately process storage cleanup job {}",
							cleanupJobId,
							exception);
				}
			}
		});
	}

	private List<DocumentDtos.ChunkSnippet> chunks(UUID documentId) {
		return jdbcTemplate.query(
				"select id, chunk_order, left(content, 500) as snippet, page_number, section, "
						+ "start_position, end_position from document_chunks "
						+ "where document_id = ? order by chunk_order",
				this::mapChunk,
				documentId);
	}

	private List<DocumentDtos.CitationSummary> citations(UUID documentId, UUID userId) {
		return jdbcTemplate.query(
				"select citation.id, citation.source_title, citation.page_number, citation.section, "
						+ "citation.chunk_position, citation.relevance_score, citation.source_deleted "
						+ "from message_citations citation "
						+ "join chat_messages message on message.id = citation.message_id "
						+ "join chat_sessions chat on chat.id = message.chat_session_id "
						+ "where citation.document_id = ? and chat.user_id = ? "
						+ "order by citation.created_at",
				this::mapCitation,
				documentId,
				userId);
	}

	private DocumentDtos.ChunkSnippet mapChunk(ResultSet result, int row) throws SQLException {
		return new DocumentDtos.ChunkSnippet(
				result.getObject("id", UUID.class),
				result.getInt("chunk_order"),
				result.getString("snippet"),
				result.getObject("page_number", Integer.class),
				result.getString("section"),
				result.getObject("start_position", Integer.class),
				result.getObject("end_position", Integer.class));
	}

	private DocumentDtos.CitationSummary mapCitation(ResultSet result, int row)
			throws SQLException {
		return new DocumentDtos.CitationSummary(
				result.getObject("id", UUID.class),
				result.getString("source_title"),
				result.getObject("page_number", Integer.class),
				result.getString("section"),
				result.getObject("chunk_position", Integer.class),
				result.getDouble("relevance_score"),
				result.getBoolean("source_deleted"));
	}

	private UserEntity requireUser(String email) {
		return userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new ApiException(
						ErrorCode.AUTHENTICATION_REQUIRED,
						HttpStatus.UNAUTHORIZED,
						"Authentication is required."));
	}

	private DocumentEntity requireOwned(UUID documentId, UUID userId) {
		return documentRepository
				.findByIdAndUserId(documentId, userId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.RESOURCE_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"The document was not found."));
	}

	private DocumentEntity requireOwnedWithLock(UUID documentId, UUID userId) {
		return documentRepository
				.findWithLockByIdAndUserId(documentId, userId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.RESOURCE_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"The document was not found."));
	}
}
