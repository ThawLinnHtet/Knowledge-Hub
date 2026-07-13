package com.knowledgehub.api.ingestion;

import java.io.InputStream;

public interface DocumentTextExtractor {

	String extract(InputStream content, String filename, String mediaType);
}
