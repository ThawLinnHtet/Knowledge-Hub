package com.knowledgehub.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

	@Test
	void splitsTextInOrderWithConfiguredOverlapAndPositions() {
		DocumentChunker chunker = new DocumentChunker(10, 3);

		var chunks = chunker.chunk("0123456789abcdefghij");

		assertThat(chunks).extracting(DocumentChunker.Chunk::content)
				.containsExactly("0123456789", "789abcdefg", "efghij");
		assertThat(chunks).extracting(DocumentChunker.Chunk::startPosition)
				.containsExactly(0, 7, 14);
		assertThat(chunks).extracting(DocumentChunker.Chunk::endPosition)
				.containsExactly(10, 17, 20);
	}

	@Test
	void avoidsEmptyTrailingChunks() {
		DocumentChunker chunker = new DocumentChunker(10, 2);

		assertThat(chunker.chunk("short")).singleElement().satisfies(chunk -> {
			assertThat(chunk.content()).isEqualTo("short");
			assertThat(chunk.characterCount()).isEqualTo(5);
		});
	}

	@Test
	void prefersParagraphBoundariesWhenAvailable() {
		DocumentChunker chunker = new DocumentChunker(16, 2);

		var chunks = chunker.chunk("first para\n\nsecond paragraph");

		assertThat(chunks.getFirst().content()).isEqualTo("first para\n\n");
		assertThat(chunks.getFirst().endPosition()).isEqualTo(12);
	}
}
