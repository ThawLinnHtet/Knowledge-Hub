package com.knowledgehub.api.documents;

import com.knowledgehub.api.collections.CollectionEntity;
import com.knowledgehub.api.users.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class DocumentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_id", nullable = false)
	private CollectionEntity collection;

	@Column(name = "original_filename", nullable = false, length = 512)
	private String originalFilename;

	@Column(name = "object_key", nullable = false, unique = true, length = 512)
	private String objectKey;

	@Column(name = "media_type", nullable = false)
	private String mediaType;

	@Column(name = "file_extension", nullable = false, length = 32)
	private String fileExtension;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "sha256_hash", nullable = false, length = 64)
	private String sha256Hash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.PENDING;

	@Column(name = "failure_code", length = 64)
	private String failureCode;

	@Column(name = "failure_message", length = 1000)
	private String failureMessage;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(nullable = false)
	private boolean retryable;

	@Column(name = "next_retry_at")
	private Instant nextRetryAt;

	@Column(name = "processing_lock_id")
	private UUID processingLockId;

	@Column(name = "processing_lock_expires_at")
	private Instant processingLockExpiresAt;

	@Column(name = "processing_started_at")
	private Instant processingStartedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public enum Status {
		PENDING,
		PROCESSING,
		READY,
		FAILED
	}
}
