package com.knowledgehub.api.search;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SearchDtos {

	private SearchDtos() {}

	public enum Mode {
		KEYWORD,
		SEMANTIC,
		HYBRID
	}

	public record CollectionRef(UUID id, String name) {}

	public record SearchItem(
			UUID chunkId,
			UUID documentId,
			String filename,
			CollectionRef collection,
			String mediaType,
			String fileExtension,
			Instant uploadedAt,
			int chunkOrder,
			String snippet,
			Integer pageNumber,
			String section,
			Integer startPosition,
			Integer endPosition,
			double score,
			double keywordScore,
			double semanticScore,
			String matchType) {}

	public record SearchResponse(Mode mode, List<SearchItem> items) {}

	public record SearchFilters(
			UUID collectionId,
			List<UUID> documentIds,
			String fileExtension,
			Instant uploadedFrom,
			Instant uploadedTo) {}
}
