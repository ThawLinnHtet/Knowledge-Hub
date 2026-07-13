package com.knowledgehub.api.ingestion;

import com.knowledgehub.api.ingestion.IngestionTransactions.ClaimedDocument;
import com.knowledgehub.api.ingestion.IngestionTransactions.EmbeddedChunk;
import com.knowledgehub.api.storage.ObjectStorage;
import com.knowledgehub.api.storage.ObjectStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionProcessor {

	private final ObjectStorage objectStorage;
	private final DocumentTextExtractor textExtractor;
	private final DocumentChunker chunker;
	private final DocumentEmbeddingClient embeddingClient;
	private final IngestionTransactions transactions;
	private final IngestionProperties properties;

	public void process(ClaimedDocument document) {
		try {
			String text = extract(document);
			if (text.length() > properties.maxExtractedCharacters()) {
				throw new IngestionException(
						"LIMIT_EXCEEDED",
						"The extracted document exceeds the configured character limit.",
						false);
			}
			List<DocumentChunker.Chunk> chunks = chunker.chunk(text);
			if (chunks.isEmpty()) {
				throw new IngestionException(
						"PROCESSING_FAILED", "The document contains no extractable text.", false);
			}
			if (chunks.size() > properties.maxChunks()) {
				throw new IngestionException(
						"LIMIT_EXCEEDED",
						"The document exceeds the configured chunk limit.",
						false);
			}
			List<EmbeddedChunk> embedded = new ArrayList<>(chunks.size());
			for (DocumentChunker.Chunk chunk : chunks) {
				float[] embedding = embeddingClient.embed(chunk.content());
				if (embedding.length != embeddingClient.dimension()) {
					throw new IngestionException(
							"PROVIDER_ERROR", "The embedding dimension is invalid.", false);
				}
				embedded.add(new EmbeddedChunk(chunk, embedding));
			}
			transactions.complete(
					document,
					embedded,
					embeddingClient.model(),
					embeddingClient.dimension(),
					Instant.now());
		} catch (IngestionException exception) {
			transactions.fail(
					document,
					exception.code(),
					exception.getMessage(),
					exception.retryable(),
					Instant.now());
		} catch (ObjectStorageException exception) {
			transactions.fail(
					document,
					"STORAGE_ERROR",
					"The original document is temporarily unavailable.",
					true,
					Instant.now());
		} catch (RuntimeException exception) {
			transactions.fail(
					document,
					"PROCESSING_FAILED",
					"The document could not be processed.",
					false,
					Instant.now());
		}
	}

	private String extract(ClaimedDocument document) {
		try (InputStream content = objectStorage.get(document.userId(), document.objectKey())) {
			return textExtractor.extract(content, document.filename(), document.mediaType());
		} catch (ObjectStorageException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new ObjectStorageException("The original document could not be closed.", exception);
		}
	}
}
