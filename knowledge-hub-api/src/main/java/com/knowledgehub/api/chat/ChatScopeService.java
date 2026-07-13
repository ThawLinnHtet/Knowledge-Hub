package com.knowledgehub.api.chat;

import com.knowledgehub.api.chat.ChatDtos.Scope;
import com.knowledgehub.api.chat.ChatDtos.ScopeType;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatScopeService {

	private final JdbcTemplate jdbcTemplate;
	private final ChatProperties properties;

	public Scope validate(UUID userId, Scope requested) {
		if (requested == null || requested.type() == null || requested.type() == ScopeType.ALL) {
			return new Scope(ScopeType.ALL, null, List.of());
		}
		if (requested.type() == ScopeType.COLLECTION) {
			if (requested.collectionId() == null || !existsCollection(userId, requested.collectionId())) {
				throw notFound("The collection was not found.");
			}
			return new Scope(ScopeType.COLLECTION, requested.collectionId(), List.of());
		}
		List<UUID> ids = requested.documentIds() == null
				? List.of()
				: new ArrayList<>(requested.documentIds().stream().distinct().toList());
		ids.sort(Comparator.naturalOrder());
		if (ids.isEmpty() || ids.size() > properties.maxScopeDocuments()) {
			throw validation("The document scope is invalid.");
		}
		Integer owned = jdbcTemplate.queryForObject(
				"select count(*) from documents where user_id = ? and id = any (?)",
				Integer.class,
				userId,
				ids.toArray(UUID[]::new));
		if (owned == null || owned != ids.size()) {
			throw notFound("One or more documents were not found.");
		}
		return new Scope(ScopeType.DOCUMENTS, null, List.copyOf(ids));
	}

	private boolean existsCollection(UUID userId, UUID collectionId) {
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from collections where user_id = ? and id = ?",
				Integer.class,
				userId,
				collectionId);
		return count != null && count == 1;
	}

	private ApiException notFound(String message) {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
	}

	private ApiException validation(String message) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
	}
}
