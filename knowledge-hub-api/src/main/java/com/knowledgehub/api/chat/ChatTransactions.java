package com.knowledgehub.api.chat;

import com.knowledgehub.api.ai.ChatCompletionClient.HistoryMessage;
import com.knowledgehub.api.chat.ChatDtos.Scope;
import com.knowledgehub.api.chat.ChatDtos.ScopeType;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.search.RagRetrievalService.RetrievedChunk;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ChatTransactions {

	private final JdbcTemplate jdbcTemplate;
	private final ChatService chatService;
	private final ChatScopeService scopeService;
	private final ChatProperties properties;
	private final ObjectMapper objectMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Turn begin(String email, UUID chatId, String rawContent, Scope requestedScope) {
		String content = rawContent == null ? "" : rawContent.strip();
		if (content.isBlank() || content.length() > properties.maxMessageCharacters()) {
			throw new ApiException(
					ErrorCode.VALIDATION_FAILED,
					HttpStatus.BAD_REQUEST,
					"The chat message is invalid.");
		}
		UUID userId = chatService.userId(email);
		List<SessionScope> sessions = jdbcTemplate.query(
				"select scope_type, collection_id, document_ids::text from chat_sessions "
						+ "where id = ? and user_id = ? for update",
				(result, row) -> new SessionScope(
						ScopeType.valueOf(result.getString(1)),
						result.getObject(2, UUID.class),
						parseIds(result.getString(3))),
				chatId,
				userId);
		if (sessions.isEmpty()) throw chatService.chatNotFound();
		SessionScope stored = sessions.getFirst();
		Scope effective = requestedScope == null
				? scopeService.validate(
						userId, new Scope(stored.type(), stored.collectionId(), stored.documentIds()))
				: scopeService.validate(userId, requestedScope);
		String snapshot = chatService.scopeJson(effective);
		if (requestedScope != null) {
			jdbcTemplate.update(
					"update chat_sessions set scope_type = ?, collection_id = ?, "
							+ "document_ids = cast(? as jsonb), updated_at = now() where id = ?",
					effective.type().name(),
					effective.collectionId(),
					idsJson(effective.documentIds()),
					chatId);
		}
		jdbcTemplate.update(
				"update chat_messages set status = 'FAILED', updated_at = now() "
						+ "where chat_session_id = ? and role = 'ASSISTANT' and status = 'PENDING' "
						+ "and updated_at < now() - (? * interval '1 millisecond')",
				chatId,
				properties.streamTimeout().toMillis());
		UUID userMessageId = jdbcTemplate.queryForObject(
				"insert into chat_messages (chat_session_id, role, content, status, scope_snapshot) "
						+ "values (?, 'USER', ?, 'COMPLETE', cast(? as jsonb)) returning id",
				UUID.class,
				chatId,
				content,
				snapshot);
		try {
			UUID assistantMessageId = jdbcTemplate.queryForObject(
					"insert into chat_messages (chat_session_id, role, content, status, scope_snapshot) "
							+ "values (?, 'ASSISTANT', '', 'PENDING', cast(? as jsonb)) returning id",
					UUID.class,
					chatId,
					snapshot);
			return new Turn(
					chatId, userId, userMessageId, assistantMessageId, email, content, effective);
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(
					ErrorCode.CHAT_STREAM_IN_PROGRESS,
					HttpStatus.CONFLICT,
					"A chat response is already in progress.");
		}
	}

	public List<HistoryMessage> history(UUID chatId, UUID currentUserMessageId) {
		return jdbcTemplate.query(
				"select role, content from chat_messages where chat_session_id = ? "
						+ "and status = 'COMPLETE' and id <> ? "
						+ "order by created_at desc, id desc limit ?",
				(result, row) -> new HistoryMessage(result.getString(1), result.getString(2)),
				chatId,
				currentUserMessageId,
				properties.maxHistoryMessages())
				.reversed();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void complete(Turn turn, String answer, List<RetrievedChunk> cited) {
		int updated = jdbcTemplate.update(
				"update chat_messages set content = ?, status = 'COMPLETE', updated_at = now() "
						+ "where id = ? and status = 'PENDING'",
				answer,
				turn.assistantMessageId());
		if (updated == 0) throw new IllegalStateException("The pending assistant message is missing.");
		for (int index = 0; index < cited.size(); index++) {
			RetrievedChunk source = cited.get(index);
			boolean exists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
					"select exists(select 1 from document_chunks where id = ? and document_id = ?)",
					Boolean.class,
					source.item().chunkId(),
					source.item().documentId()));
			jdbcTemplate.update(
					"insert into message_citations (message_id, document_id, chunk_id, source_title, "
							+ "page_number, section, chunk_position, relevance_score, source_deleted, "
							+ "citation_order) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
					turn.assistantMessageId(),
					exists ? source.item().documentId() : null,
					exists ? source.item().chunkId() : null,
					source.item().filename(),
					source.item().pageNumber(),
					source.item().section(),
					source.item().chunkOrder(),
					source.item().score(),
					!exists,
					index + 1);
		}
		jdbcTemplate.update("update chat_sessions set updated_at = now() where id = ?", turn.chatId());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fail(UUID assistantMessageId) {
		jdbcTemplate.update(
				"update chat_messages set status = 'FAILED', content = '', updated_at = now() "
						+ "where id = ? and status = 'PENDING'",
				assistantMessageId);
	}

	private List<UUID> parseIds(String json) {
		try {
			List<UUID> ids = new java.util.ArrayList<>();
			objectMapper.readTree(json).forEach(node -> ids.add(UUID.fromString(node.asText())));
			return List.copyOf(ids);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read chat document scope.", exception);
		}
	}

	private String idsJson(List<UUID> ids) {
		try {
			return objectMapper.writeValueAsString(ids);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not serialize chat document scope.", exception);
		}
	}

	private record SessionScope(ScopeType type, UUID collectionId, List<UUID> documentIds) {}

	public record Turn(
			UUID chatId,
			UUID userId,
			UUID userMessageId,
			UUID assistantMessageId,
			String email,
			String question,
			Scope scope) {}
}
