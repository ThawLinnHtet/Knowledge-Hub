package com.knowledgehub.api.ai;

import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.chat.fake-ai", havingValue = "true", matchIfMissing = true)
public class FakeChatCompletionClient implements ChatCompletionClient {

	@Override
	public void stream(GroundedChatRequest request, Consumer<String> deltaConsumer) {
		Evidence source = request.evidence().getFirst();
		String answer = "The uploaded source \"" + source.title()
				+ "\" contains relevant information for this question [" + source.sourceId() + "].";
		int middle = answer.length() / 2;
		deltaConsumer.accept(answer.substring(0, middle));
		deltaConsumer.accept(answer.substring(middle));
	}
}
