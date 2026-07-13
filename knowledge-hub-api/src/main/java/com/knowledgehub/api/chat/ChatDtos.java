package com.knowledgehub.api.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

	private ChatDtos() {}

	public enum ScopeType { ALL, COLLECTION, DOCUMENTS }

	public record Scope(ScopeType type, UUID collectionId, List<UUID> documentIds) {}

	public record CreateRequest(@Size(max = 255) String title, @Valid Scope scope) {}

	public record RenameRequest(@NotBlank @Size(max = 255) String title) {}

	public record SendRequest(@NotBlank @Size(max = 4000) String content, @Valid Scope scope) {}

	public record ChatResponse(
			UUID id, String title, Scope scope, Instant createdAt, Instant updatedAt) {}

	public record CitationResponse(
			int order,
			UUID documentId,
			UUID chunkId,
			String sourceTitle,
			Integer pageNumber,
			String section,
			Integer chunkPosition,
			double relevanceScore,
			boolean sourceDeleted) {}

	public record MessageResponse(
			UUID id,
			String role,
			String status,
			String content,
			Scope scope,
			List<CitationResponse> citations,
			Instant createdAt) {}
}
