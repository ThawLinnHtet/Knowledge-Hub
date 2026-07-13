package com.knowledgehub.api.documents;

import com.knowledgehub.api.common.ErrorResponse;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentDtos {

	private DocumentDtos() {}

	public record CollectionRef(UUID id, String name) {}

	public record DocumentResponse(
			UUID id,
			String filename,
			String status,
			CollectionRef collection,
			String mediaType,
			String fileExtension,
			long sizeBytes,
			Instant uploadedAt,
			String failureCode,
			String failureMessage,
			int retryCount,
			boolean retryable,
			Instant nextRetryAt,
			Instant processingStartedAt,
			Instant processedAt,
			Instant updatedAt) {}

	public record UploadResponse(List<UploadItem> items) {}

	public record DocumentPage(
			List<DocumentResponse> items,
			int page,
			int size,
			long totalElements,
			int totalPages) {}

	public record ChunkSnippet(
			UUID id,
			int chunkOrder,
			String snippet,
			Integer pageNumber,
			String section,
			Integer startPosition,
			Integer endPosition) {}

	public record CitationSummary(
			UUID id,
			String sourceTitle,
			Integer pageNumber,
			String section,
			Integer chunkPosition,
			double relevanceScore,
			boolean sourceDeleted) {}

	public record DocumentDetailResponse(
			UUID id,
			String filename,
			String status,
			CollectionRef collection,
			String mediaType,
			String fileExtension,
			long sizeBytes,
			Instant uploadedAt,
			String failureCode,
			String failureMessage,
			int retryCount,
			boolean retryable,
			Instant nextRetryAt,
			Instant processingStartedAt,
			Instant processedAt,
			Instant updatedAt,
			List<ChunkSnippet> chunks,
			List<CitationSummary> citations) {

		static DocumentDetailResponse from(
				DocumentResponse document,
				List<ChunkSnippet> chunks,
				List<CitationSummary> citations) {
			return new DocumentDetailResponse(
					document.id(),
					document.filename(),
					document.status(),
					document.collection(),
					document.mediaType(),
					document.fileExtension(),
					document.sizeBytes(),
					document.uploadedAt(),
					document.failureCode(),
					document.failureMessage(),
					document.retryCount(),
					document.retryable(),
					document.nextRetryAt(),
					document.processingStartedAt(),
					document.processedAt(),
					document.updatedAt(),
					chunks,
					citations);
		}
	}

	public record DownloadUrlResponse(URI url, Instant expiresAt) {}

	public record UploadItem(
			int fileIndex,
			String filename,
			String status,
			DocumentResponse document,
			ErrorResponse error) {

		static UploadItem uploaded(int index, DocumentResponse document) {
			return new UploadItem(index, document.filename(), "UPLOADED", document, null);
		}

		static UploadItem skipped(int index, String filename) {
			return new UploadItem(index, filename, "SKIPPED", null, null);
		}

		static UploadItem rejected(int index, String filename, ErrorResponse error) {
			return new UploadItem(index, filename, "REJECTED", null, error);
		}
	}
}
