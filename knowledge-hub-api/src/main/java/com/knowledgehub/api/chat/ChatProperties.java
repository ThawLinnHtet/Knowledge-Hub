package com.knowledgehub.api.chat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.chat")
@Validated
public record ChatProperties(
		boolean fakeAi,
		@Positive int maxMessageCharacters,
		@Positive int maxScopeDocuments,
		@Positive int maxHistoryMessages,
		@Positive int maxPromptTokens,
		@Positive int retrievedChunkLimit,
		@DecimalMin("0.0") @DecimalMax("1.0") double minimumKeywordScore,
		@DecimalMin("0.0") @DecimalMax("1.0") double minimumSemanticScore,
		Duration streamTimeout,
		Duration heartbeatInterval) {

	public ChatProperties {
		if (streamTimeout == null || streamTimeout.toMillis() < 1) {
			throw new IllegalArgumentException("Chat stream timeout must be positive.");
		}
		if (heartbeatInterval == null
				|| heartbeatInterval.toMillis() < 1
				|| heartbeatInterval.compareTo(streamTimeout) >= 0) {
			throw new IllegalArgumentException(
					"Chat heartbeat interval must be positive and shorter than the stream timeout.");
		}
	}
}
