package com.knowledgehub.api.ai;

import java.util.List;
import java.util.function.Consumer;

public interface ChatCompletionClient {

	void stream(GroundedChatRequest request, Consumer<String> deltaConsumer);

	record GroundedChatRequest(String question, List<HistoryMessage> history, List<Evidence> evidence) {}

	record HistoryMessage(String role, String content) {}

	record Evidence(String sourceId, String title, String content) {}
}
