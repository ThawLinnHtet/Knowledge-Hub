package com.knowledgehub.api.chat;

import com.knowledgehub.api.ai.ChatCompletionClient;
import com.knowledgehub.api.ai.ChatCompletionClient.Evidence;
import com.knowledgehub.api.ai.ChatCompletionClient.GroundedChatRequest;
import com.knowledgehub.api.chat.ChatDtos.ScopeType;
import com.knowledgehub.api.chat.ChatTransactions.Turn;
import com.knowledgehub.api.search.RagRetrievalService;
import com.knowledgehub.api.search.RagRetrievalService.RetrievedChunk;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
public class ChatStreamService {

	private static final Pattern CITATION = Pattern.compile("\\[S(\\d+)]");
	private static final String REFUSAL =
			"I couldn't find sufficient support for that answer in the uploaded documents.";
	private final ChatTransactions transactions;
	private final RagRetrievalService retrievalService;
	private final ChatCompletionClient chatClient;
	private final ChatProperties properties;
	private final Executor executor;
	private final ScheduledExecutorService heartbeatScheduler;

	public ChatStreamService(
			ChatTransactions transactions,
			RagRetrievalService retrievalService,
			ChatCompletionClient chatClient,
			ChatProperties properties,
			@Qualifier("chatExecutor") Executor executor,
			@Qualifier("chatHeartbeatScheduler") ScheduledExecutorService heartbeatScheduler) {
		this.transactions = transactions;
		this.retrievalService = retrievalService;
		this.chatClient = chatClient;
		this.properties = properties;
		this.executor = executor;
		this.heartbeatScheduler = heartbeatScheduler;
	}

	public SseEmitter stream(
			String email, java.util.UUID chatId, String content, ChatDtos.Scope requestedScope) {
		Turn turn = transactions.begin(email, chatId, content, requestedScope);
		SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
		AtomicBoolean terminated = new AtomicBoolean();
		emitter.onTimeout(() -> terminated.set(true));
		emitter.onError(error -> terminated.set(true));
		try {
			executor.execute(() -> process(turn, emitter, terminated));
		} catch (RuntimeException exception) {
			transactions.fail(turn.assistantMessageId());
			throw new ApiException(
					ErrorCode.PROVIDER_ERROR,
					HttpStatus.SERVICE_UNAVAILABLE,
					"The chat service is temporarily unavailable.");
		}
		return emitter;
	}

	private void process(Turn turn, SseEmitter emitter, AtomicBoolean terminated) {
		ScheduledFuture<?> heartbeatTask = null;
		try {
			send(emitter, "started", Map.of(
					"sessionId", turn.chatId(),
					"userMessageId", turn.userMessageId(),
					"assistantMessageId", turn.assistantMessageId(),
					"scope", turn.scope()));
			long heartbeatMillis = properties.heartbeatInterval().toMillis();
			heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(
					() -> heartbeat(emitter, terminated),
					heartbeatMillis,
					heartbeatMillis,
					TimeUnit.MILLISECONDS);
			List<RetrievedChunk> retrieved = retrievalService.retrieve(
					turn.email(),
					turn.question(),
					turn.scope().type() == ScopeType.COLLECTION ? turn.scope().collectionId() : null,
					turn.scope().type() == ScopeType.DOCUMENTS ? turn.scope().documentIds() : List.of(),
					properties.retrievedChunkLimit());
			List<RetrievedChunk> evidence = retrieved.stream().filter(this::strongEvidence).toList();
			String answer;
			String evidenceStatus;
			List<RetrievedChunk> cited = List.of();
			if (evidence.isEmpty()) {
				answer = REFUSAL;
				evidenceStatus = "INSUFFICIENT";
			} else {
				var history = transactions.history(turn.chatId(), turn.userMessageId());
				List<Evidence> sources = budgetedEvidence(evidence, turn.question(), history)
						.stream()
						.map(indexed -> new Evidence(
								"S" + (indexed.index() + 1),
								indexed.chunk().item().filename(),
								indexed.content()))
						.toList();
				StringBuilder streamed = new StringBuilder();
				chatClient.stream(new GroundedChatRequest(turn.question(), history, sources), delta -> {
					if (terminated.get()) throw new IllegalStateException("The chat stream ended.");
					streamed.append(delta);
				});
				answer = streamed.toString();
				cited = citedEvidence(answer, evidence);
				if (cited.isEmpty()) {
					answer = REFUSAL;
					evidenceStatus = "INSUFFICIENT";
					log.atWarn()
							.addKeyValue("chatId", turn.chatId())
							.log("uncited provider response replaced with grounded refusal");
				} else {
					evidenceStatus = "SUPPORTED";
				}
			}
			if (terminated.get()) {
				throw new IllegalStateException("The chat stream ended before completion.");
			}
			transactions.complete(turn, answer, cited);
			for (String delta : deltas(answer)) {
				send(emitter, "delta", Map.of("messageId", turn.assistantMessageId(), "text", delta));
			}
			log.atInfo()
					.addKeyValue("chatId", turn.chatId())
					.addKeyValue("aiProvider", properties.fakeAi() ? "fake" : "openai-compatible")
					.addKeyValue("retrievedChunkCount", retrieved.size())
					.addKeyValue("evidenceStatus", evidenceStatus)
					.log("chat response completed");
			send(emitter, "completed", Map.of(
					"messageId", turn.assistantMessageId(),
					"content", answer,
					"evidenceStatus", evidenceStatus));
			emitter.complete();
		} catch (Exception exception) {
			log.atError()
					.setCause(exception)
					.addKeyValue("chatId", turn.chatId())
					.log("chat response failed");
			transactions.fail(turn.assistantMessageId());
			try {
				if (!terminated.get()) {
					send(emitter, "error", Map.of(
							"code", "PROVIDER_ERROR",
							"message", "The chat response could not be completed.",
							"recoverable", true));
					emitter.complete();
				} else {
					emitter.completeWithError(exception);
				}
			} catch (RuntimeException ignored) {
				emitter.completeWithError(exception);
			}
		} finally {
			if (heartbeatTask != null) heartbeatTask.cancel(false);
		}
	}

	private List<IndexedEvidence> budgetedEvidence(
			List<RetrievedChunk> evidence,
			String question,
			List<com.knowledgehub.api.ai.ChatCompletionClient.HistoryMessage> history) {
		int remaining = properties.maxPromptTokens() * 4 - question.length()
				- history.stream().mapToInt(message -> message.content().length()).sum();
		List<IndexedEvidence> budgeted = new java.util.ArrayList<>();
		for (int index = 0; index < evidence.size() && remaining > 0; index++) {
			RetrievedChunk chunk = evidence.get(index);
			String content = chunk.content().substring(0, Math.min(chunk.content().length(), remaining));
			if (!content.isBlank()) {
				budgeted.add(new IndexedEvidence(index, chunk, content));
				remaining -= content.length();
			}
		}
		if (budgeted.isEmpty()) {
			throw new ApiException(
					ErrorCode.LIMIT_EXCEEDED,
					HttpStatus.BAD_REQUEST,
					"The chat prompt exceeds the configured token budget.");
		}
		return List.copyOf(budgeted);
	}

	private List<RetrievedChunk> citedEvidence(String answer, List<RetrievedChunk> evidence) {
		var matcher = CITATION.matcher(answer);
		java.util.LinkedHashSet<Integer> indexes = new java.util.LinkedHashSet<>();
		while (matcher.find()) {
			int index = Integer.parseInt(matcher.group(1)) - 1;
			if (index < 0 || index >= evidence.size()) {
				throw new IllegalStateException("The answer cited an unknown source.");
			}
			indexes.add(index);
		}
		return indexes.stream().map(evidence::get).toList();
	}

	private record IndexedEvidence(int index, RetrievedChunk chunk, String content) {}

	private boolean strongEvidence(RetrievedChunk chunk) {
		return chunk.item().keywordScore() >= properties.minimumKeywordScore()
				|| chunk.item().semanticScore() >= properties.minimumSemanticScore();
	}

	private List<String> deltas(String answer) {
		int middle = Math.max(1, answer.length() / 2);
		return middle == answer.length()
				? List.of(answer)
				: List.of(answer.substring(0, middle), answer.substring(middle));
	}

	private void send(SseEmitter emitter, String event, Object data) {
		try {
			emitter.send(SseEmitter.event().name(event).data(data));
		} catch (IOException exception) {
			throw new IllegalStateException("The chat stream was interrupted.", exception);
		}
	}

	private void heartbeat(SseEmitter emitter, AtomicBoolean terminated) {
		if (terminated.get()) return;
		try {
			emitter.send(SseEmitter.event().comment("grounding"));
		} catch (IOException | IllegalStateException exception) {
			terminated.set(true);
		}
	}
}
