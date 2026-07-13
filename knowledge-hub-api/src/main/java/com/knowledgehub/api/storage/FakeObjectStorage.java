package com.knowledgehub.api.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "fake", matchIfMissing = true)
public class FakeObjectStorage implements ObjectStorage {

	private final Map<StorageKey, StoredObject> objects = new ConcurrentHashMap<>();

	@Override
	public void put(
			UUID userId, String objectKey, InputStream content, long size, String mediaType) {
		if (size < 0 || size >= Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Stored object size is invalid.");
		}
		try {
			byte[] bytes = content.readNBytes((int) size + 1);
			if (bytes.length != size) {
				throw new IllegalArgumentException("Stored object size does not match declared size.");
			}
			objects.put(new StorageKey(userId, objectKey), new StoredObject(bytes, mediaType));
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read stored object content.", exception);
		}
	}

	@Override
	public InputStream get(UUID userId, String objectKey) {
		StoredObject stored = objects.get(new StorageKey(userId, objectKey));
		if (stored == null) {
			throw new ObjectStorageException("The stored object does not exist.");
		}
		return new ByteArrayInputStream(stored.content());
	}

	@Override
	public URI createDownloadUrl(UUID userId, String objectKey, Duration ttl) {
		if (!objects.containsKey(new StorageKey(userId, objectKey))) {
			throw new ObjectStorageException("The stored object does not exist.");
		}
		return URI.create(
				"https://fake-storage.local/" + userId + "/" + objectKey + "?ttl=" + ttl.toSeconds());
	}

	@Override
	public void delete(UUID userId, String objectKey) {
		objects.remove(new StorageKey(userId, objectKey));
	}

	@Override
	public void deleteAll(UUID userId) {
		objects.keySet().removeIf(key -> key.userId().equals(userId));
	}

	int objectCount() {
		return objects.size();
	}

	private record StoredObject(byte[] content, String mediaType) {}

	private record StorageKey(UUID userId, String objectKey) {}
}
