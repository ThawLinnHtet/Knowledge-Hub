package com.knowledgehub.api.ingestion;

import java.io.InputStream;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

@Component
public class TikaDocumentTextExtractor implements DocumentTextExtractor {
	private final IngestionProperties properties;

	public TikaDocumentTextExtractor(IngestionProperties properties) {
		this.properties = properties;
	}

	@Override
	public String extract(InputStream content, String filename, String mediaType) {
		Metadata metadata = new Metadata();
		metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
		metadata.set(HttpHeaders.CONTENT_TYPE, mediaType);
		BodyContentHandler handler =
				new BodyContentHandler(properties.maxExtractedCharacters() + 1);
		try {
			new AutoDetectParser().parse(content, handler, metadata, new ParseContext());
			return handler.toString().strip();
		} catch (Exception exception) {
			if (WriteLimitReachedException.isWriteLimitReached(exception)) {
				throw new IngestionException(
						"LIMIT_EXCEEDED",
						"The extracted document exceeds the configured character limit.",
						false,
						exception);
			}
			if (hasCause(exception, java.io.IOException.class)) {
				throw new IngestionException(
						"STORAGE_ERROR",
						"The original document is temporarily unavailable.",
						true,
						exception);
			}
			throw new IngestionException(
					"PROCESSING_FAILED", "The document content could not be extracted.", false, exception);
		}
	}

	private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (type.isInstance(cause)) {
				return true;
			}
		}
		return false;
	}
}
