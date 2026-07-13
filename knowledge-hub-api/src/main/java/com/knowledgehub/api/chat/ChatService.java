package com.knowledgehub.api.chat;

import com.knowledgehub.api.chat.ChatDtos.ChatResponse;
import com.knowledgehub.api.chat.ChatDtos.CitationResponse;
import com.knowledgehub.api.chat.ChatDtos.MessageResponse;
import com.knowledgehub.api.chat.ChatDtos.Scope;
import com.knowledgehub.api.chat.ChatDtos.ScopeType;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.users.UserRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ChatService {

	private final UserRepository userRepository;
	private final JdbcTemplate jdbcTemplate;
	private final ChatScopeService scopeService;
	private final ObjectMapper objectMapper;

	@Transactional
	public ChatResponse create(String email, String requestedTitle, Scope requestedScope) {
		UUID userId = userId(email);
		Scope scope = scopeService.validate(userId, requestedScope);
		String title = requestedTitle == null || requestedTitle.isBlank()
				? "New chat"
				: requestedTitle.strip();
		return jdbcTemplate.queryForObject(
				"insert into chat_sessions (user_id, title, scope_type, collection_id, document_ids) "
						+ "values (?, ?, ?, ?, cast(? as jsonb)) returning *",
				this::mapChat,
				userId,
				title,
				scope.type().name(),
				scope.collectionId(),
				documentIdsJson(scope));
	}

	public List<ChatResponse> list(String email) {
		return jdbcTemplate.query(
				"select * from chat_sessions where user_id = ? order by updated_at desc, id",
				this::mapChat,
				userId(email));
	}

	@Transactional
	public ChatResponse rename(String email, UUID chatId, String title) {
		UUID userId = userId(email);
		List<ChatResponse> updated = jdbcTemplate.query(
				"update chat_sessions set title = ?, updated_at = now() where id = ? and user_id = ? "
						+ "returning *",
				this::mapChat,
				title.strip(),
				chatId,
				userId);
		if (updated.isEmpty()) throw chatNotFound();
		return updated.getFirst();
	}

	@Transactional
	public void delete(String email, UUID chatId) {
		if (jdbcTemplate.update(
				"delete from chat_sessions where id = ? and user_id = ?", chatId, userId(email)) == 0) {
			throw chatNotFound();
		}
	}

	public List<MessageResponse> messages(String email, UUID chatId) {
		UUID userId = userId(email);
		if (jdbcTemplate.queryForObject(
				"select count(*) from chat_sessions where id = ? and user_id = ?",
				Integer.class,
				chatId,
				userId) == 0) {
			throw chatNotFound();
		}
		return jdbcTemplate.query(
				"select * from chat_messages where chat_session_id = ? order by created_at, "
						+ "case when role = 'USER' then 0 else 1 end, id",
				(result, row) -> new MessageResponse(
						result.getObject("id", UUID.class),
						result.getString("role"),
						result.getString("status"),
						result.getString("content"),
						parseScope(result.getString("scope_snapshot")),
						citations(result.getObject("id", UUID.class)),
						result.getTimestamp("created_at").toInstant()),
				chatId);
	}

	UUID userId(String email) {
		return userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new ApiException(
						ErrorCode.AUTHENTICATION_REQUIRED,
						HttpStatus.UNAUTHORIZED,
						"Authentication is required."))
				.getId();
	}

	String scopeJson(Scope scope) {
		try {
			return objectMapper.writeValueAsString(java.util.Map.of(
					"type", scope.type().name(),
					"collectionId", scope.collectionId() == null ? "" : scope.collectionId().toString(),
					"documentIds", scope.documentIds()));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not serialize chat scope.", exception);
		}
	}

	Scope parseScope(String json) {
		try {
			JsonNode node = objectMapper.readTree(json);
			ScopeType type = ScopeType.valueOf(node.get("type").asText());
			String collection = node.path("collectionId").asText("");
			List<UUID> ids = new java.util.ArrayList<>();
			node.path("documentIds").forEach(value -> ids.add(UUID.fromString(value.asText())));
			return new Scope(type, collection.isBlank() ? null : UUID.fromString(collection), List.copyOf(ids));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read chat scope.", exception);
		}
	}

	private String documentIdsJson(Scope scope) {
		try {
			return objectMapper.writeValueAsString(scope.documentIds());
		} catch (Exception exception) {
			throw new IllegalStateException("Could not serialize document scope.", exception);
		}
	}

	private ChatResponse mapChat(ResultSet result, int row) throws SQLException {
		ScopeType type = ScopeType.valueOf(result.getString("scope_type"));
		List<UUID> ids = parseDocumentIds(result.getString("document_ids"));
		return new ChatResponse(
				result.getObject("id", UUID.class),
				result.getString("title"),
				new Scope(type, result.getObject("collection_id", UUID.class), ids),
				result.getTimestamp("created_at").toInstant(),
				result.getTimestamp("updated_at").toInstant());
	}

	private List<UUID> parseDocumentIds(String json) {
		try {
			List<UUID> ids = new java.util.ArrayList<>();
			objectMapper.readTree(json).forEach(value -> ids.add(UUID.fromString(value.asText())));
			return List.copyOf(ids);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read document scope.", exception);
		}
	}

	private List<CitationResponse> citations(UUID messageId) {
		return jdbcTemplate.query(
				"select * from message_citations where message_id = ? order by citation_order",
				(result, row) -> new CitationResponse(
						result.getInt("citation_order"),
						result.getObject("document_id", UUID.class),
						result.getObject("chunk_id", UUID.class),
						result.getString("source_title"),
						result.getObject("page_number", Integer.class),
						result.getString("section"),
						result.getObject("chunk_position", Integer.class),
						result.getDouble("relevance_score"),
						result.getBoolean("source_deleted")),
				messageId);
	}

	ApiException chatNotFound() {
		return new ApiException(
				ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "The chat session was not found.");
	}
}
