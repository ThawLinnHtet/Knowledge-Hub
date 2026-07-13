package com.knowledgehub.api.ingestion;

public interface DocumentEmbeddingClient {

	float[] embed(String content);

	String model();

	int dimension();
}
