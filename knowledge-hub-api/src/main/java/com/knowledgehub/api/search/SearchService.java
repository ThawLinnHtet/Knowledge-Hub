package com.knowledgehub.api.search;

import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.ingestion.DocumentEmbeddingClient;
import com.knowledgehub.api.ingestion.IngestionException;
import com.knowledgehub.api.search.SearchDtos.Mode;
import com.knowledgehub.api.search.SearchDtos.SearchFilters;
import com.knowledgehub.api.search.SearchDtos.SearchResponse;
import com.knowledgehub.api.users.AuthenticatedUserService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

	private final AuthenticatedUserService authenticatedUsers;
	private final SearchRepository searchRepository;
	private final DocumentEmbeddingClient embeddingClient;
	private final SearchProperties properties;

	public SearchResponse search(
			String email,
			String query,
			Mode mode,
			UUID collectionId,
			List<UUID> documentIds,
			String fileExtension,
			Instant uploadedFrom,
			Instant uploadedTo,
			int limit) {
		String normalizedQuery = validate(query, uploadedFrom, uploadedTo, limit);
		UUID userId = authenticatedUsers.userId(email);
		String normalizedExtension = normalizeExtension(fileExtension);
		float[] queryEmbedding = null;
		if (mode != Mode.KEYWORD) {
			try {
				queryEmbedding = embeddingClient.embed(normalizedQuery);
			} catch (IngestionException exception) {
				throw new ApiException(
						ErrorCode.PROVIDER_ERROR,
						HttpStatus.SERVICE_UNAVAILABLE,
						"The embedding provider is unavailable.");
			}
		}
		var filters = new SearchFilters(
				collectionId,
				documentIds == null ? List.of() : List.copyOf(documentIds),
				normalizedExtension,
				uploadedFrom,
				uploadedTo);
		SearchResponse response = new SearchResponse(
				mode,
				searchRepository.search(
						userId,
						normalizedQuery,
						mode,
						queryEmbedding,
						embeddingClient.model(),
						embeddingClient.dimension(),
						filters,
						limit));
		log.atInfo()
				.addKeyValue("retrievalMode", mode)
				.addKeyValue("retrievedChunkCount", response.items().size())
				.addKeyValue("embeddingModel", mode == Mode.KEYWORD ? "none" : embeddingClient.model())
				.log("retrieval completed");
		return response;
	}

	private String validate(
			String query, Instant uploadedFrom, Instant uploadedTo, int limit) {
		if (query == null || query.isBlank()) {
			throw validation("The search query is required.");
		}
		String normalized = query.strip();
		if (normalized.length() > properties.maxQueryCharacters()) {
			throw validation("The search query is too long.");
		}
		if (uploadedFrom != null && uploadedTo != null && uploadedFrom.isAfter(uploadedTo)) {
			throw validation("The upload date range is invalid.");
		}
		if (limit < 1 || limit > properties.maxResults()) {
			throw validation("The search result limit is invalid.");
		}
		return normalized;
	}

	private String normalizeExtension(String extension) {
		if (extension == null || extension.isBlank()) {
			return null;
		}
		String normalized = extension.strip().toLowerCase(Locale.ROOT);
		return normalized.startsWith(".") ? normalized.substring(1) : normalized;
	}

	private ApiException validation(String message) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
	}
}
