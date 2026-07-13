package com.knowledgehub.api.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ingestion.fake-ai", havingValue = "false")
@Slf4j
public class ProviderDocumentEmbeddingClient implements DocumentEmbeddingClient {

	private final EmbeddingModel embeddingModel;
	private final IngestionProperties properties;

	public ProviderDocumentEmbeddingClient(
			EmbeddingModel embeddingModel, IngestionProperties properties) {
		this.embeddingModel = embeddingModel;
		this.properties = properties;
	}

	@Override
	public float[] embed(String content) {
		try {
			float[] embedding = embeddingModel.embed(content);
			if (embedding.length != dimension()) {
				throw new IngestionException(
						"PROVIDER_ERROR", "The embedding dimension is invalid.", false);
			}
			return embedding;
		} catch (IngestionException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			log.atError()
					.setCause(exception)
					.addKeyValue("embeddingModel", properties.embeddingModel())
					.log("embedding provider request failed");
			throw new IngestionException(
					"PROVIDER_ERROR", "The embedding provider is unavailable.", true, exception);
		}
	}

	@Override
	public String model() {
		return properties.embeddingModel();
	}

	@Override
	public int dimension() {
		return properties.embeddingDimension();
	}
}
