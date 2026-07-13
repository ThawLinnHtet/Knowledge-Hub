package com.knowledgehub.api.ingestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.ingestion")
@Validated
public record IngestionProperties(
		@Positive int maxExtractedCharacters,
		@Positive int maxChunks,
		@Positive int chunkSize,
		@PositiveOrZero int chunkOverlap,
		@Positive int embeddingDimension,
		@NotBlank String embeddingModel,
		boolean fakeAi,
		@NotNull Duration leaseDuration,
		@Positive int maxRetries,
		@NotNull Duration retryDelay) {

	public IngestionProperties {
		if (chunkOverlap >= chunkSize) {
			throw new IllegalArgumentException("Chunk overlap must be smaller than chunk size.");
		}
		if (embeddingDimension != 1024) {
			throw new IllegalArgumentException("The active embedding dimension must be 1024.");
		}
		if (leaseDuration != null && !leaseDuration.isPositive()) {
			throw new IllegalArgumentException("The ingestion lease duration must be positive.");
		}
		if (retryDelay != null && !retryDelay.isPositive()) {
			throw new IllegalArgumentException("The ingestion retry delay must be positive.");
		}
	}
}
