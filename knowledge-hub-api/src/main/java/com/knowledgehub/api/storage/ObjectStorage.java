package com.knowledgehub.api.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

public interface ObjectStorage {

	void put(UUID userId, String objectKey, InputStream content, long size, String mediaType);

	InputStream get(UUID userId, String objectKey);

	URI createDownloadUrl(UUID userId, String objectKey, Duration ttl);

	void delete(UUID userId, String objectKey);

	void deleteAll(UUID userId);
}
