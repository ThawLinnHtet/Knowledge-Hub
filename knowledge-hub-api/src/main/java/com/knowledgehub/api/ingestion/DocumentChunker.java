package com.knowledgehub.api.ingestion;

import java.util.ArrayList;
import java.util.List;

public class DocumentChunker {

	private final int chunkSize;
	private final int overlap;

	public DocumentChunker(int chunkSize, int overlap) {
		if (chunkSize < 1 || overlap < 0 || overlap >= chunkSize) {
			throw new IllegalArgumentException("Chunk size and overlap are invalid.");
		}
		this.chunkSize = chunkSize;
		this.overlap = overlap;
	}

	public List<Chunk> chunk(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<Chunk> chunks = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int end = preferredBoundary(text, start, Math.min(start + chunkSize, text.length()));
			String content = text.substring(start, end);
			chunks.add(new Chunk(content, start, end, content.length(), estimateTokens(content)));
			if (end == text.length()) {
				break;
			}
			start = codePointBoundary(text, end - overlap);
		}
		return List.copyOf(chunks);
	}

	private int preferredBoundary(String text, int start, int target) {
		if (target == text.length()) {
			return target;
		}
		int minimum = Math.max(start + chunkSize / 2, start + overlap + 1);
		int paragraph = text.lastIndexOf("\n\n", target - 1);
		if (paragraph >= minimum) {
			return paragraph + 2;
		}
		int line = text.lastIndexOf('\n', target - 1);
		if (line >= minimum) {
			return line + 1;
		}
		int word = text.lastIndexOf(' ', target - 1);
		if (word >= minimum) {
			return word + 1;
		}
		return codePointBoundary(text, target);
	}

	private int codePointBoundary(String text, int position) {
		if (position > 0
				&& position < text.length()
				&& Character.isLowSurrogate(text.charAt(position))
				&& Character.isHighSurrogate(text.charAt(position - 1))) {
			return position + 1;
		}
		return position;
	}

	private int estimateTokens(String content) {
		return Math.max(1, (content.length() + 3) / 4);
	}

	public record Chunk(
			String content,
			int startPosition,
			int endPosition,
			int characterCount,
			int tokenEstimate) {}
}
