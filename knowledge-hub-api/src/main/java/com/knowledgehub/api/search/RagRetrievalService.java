package com.knowledgehub.api.search;

import com.knowledgehub.api.search.SearchDtos.Mode;
import com.knowledgehub.api.search.SearchDtos.SearchItem;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagRetrievalService {

	private final SearchService searchService;
	private final JdbcTemplate jdbcTemplate;

	public List<RetrievedChunk> retrieve(
			String email, String query, UUID collectionId, List<UUID> documentIds, int limit) {
		return searchService
				.search(email, query, Mode.HYBRID, collectionId, documentIds, null, null, null, limit)
				.items()
				.stream()
				.map(item -> new RetrievedChunk(item, content(item.chunkId())))
				.toList();
	}

	private String content(UUID chunkId) {
		return jdbcTemplate.queryForObject(
				"select content from document_chunks where id = ?", String.class, chunkId);
	}

	public record RetrievedChunk(SearchItem item, String content) {}
}
