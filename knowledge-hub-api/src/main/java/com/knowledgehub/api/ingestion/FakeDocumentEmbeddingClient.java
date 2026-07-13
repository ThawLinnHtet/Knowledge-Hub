package com.knowledgehub.api.ingestion;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ingestion.fake-ai", havingValue = "true", matchIfMissing = true)
public class FakeDocumentEmbeddingClient implements DocumentEmbeddingClient {

	private final int dimension;

	public FakeDocumentEmbeddingClient(IngestionProperties properties) {
		this.dimension = properties.embeddingDimension();
	}

	@Override
	public float[] embed(String content) {
		byte[] digest;
		try {
			digest = MessageDigest.getInstance("SHA-256")
					.digest(content.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
		float[] vector = new float[dimension];
		for (int index = 0; index < vector.length; index++) {
			int offset = (index * Integer.BYTES) % digest.length;
			int value = ByteBuffer.wrap(digest, offset, Integer.BYTES).getInt();
			vector[index] = value / (float) Integer.MAX_VALUE;
		}
		return vector;
	}

	@Override
	public String model() {
		return "fake-embedding";
	}

	@Override
	public int dimension() {
		return dimension;
	}
}
