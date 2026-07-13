package com.knowledgehub.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeObjectStorageTest {

	private final FakeObjectStorage storage = new FakeObjectStorage();
	private final UUID userId = UUID.randomUUID();

	@Test
	void storesReadsAuthorizesAndDeletesPrivateObjectsThroughTheStorageSeam() throws Exception {
		byte[] content = "document".getBytes(StandardCharsets.UTF_8);

		storage.put(
				userId, "opaque/key", new ByteArrayInputStream(content), content.length, "text/plain");

		assertThat(storage.objectCount()).isEqualTo(1);
		try (var stored = storage.get(userId, "opaque/key")) {
			assertThat(stored.readAllBytes()).isEqualTo(content);
		}
		assertThat(storage.createDownloadUrl(userId, "opaque/key", Duration.ofMinutes(5)).toString())
				.isEqualTo("https://fake-storage.local/" + userId + "/opaque/key?ttl=300");
		storage.delete(userId, "opaque/key");
		assertThat(storage.objectCount()).isZero();
		assertThatThrownBy(
						() -> storage.createDownloadUrl(userId, "opaque/key", Duration.ofMinutes(5)))
				.isInstanceOf(ObjectStorageException.class);
		assertThatThrownBy(() -> storage.get(userId, "opaque/key"))
				.isInstanceOf(ObjectStorageException.class);
	}

	@Test
	void rejectsContentThatExceedsItsDeclaredSize() {
		byte[] content = "document".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> storage.put(
						userId,
						"opaque/key",
						new ByteArrayInputStream(content),
						content.length - 1,
						"text/plain"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(storage.objectCount()).isZero();
	}

	@Test
	void isolatesAndDeletesObjectsByOwner() {
		UUID otherUserId = UUID.randomUUID();
		byte[] content = "document".getBytes(StandardCharsets.UTF_8);
		storage.put(
				userId, "same-key", new ByteArrayInputStream(content), content.length, "text/plain");
		storage.put(
				otherUserId,
				"same-key",
				new ByteArrayInputStream(content),
				content.length,
				"text/plain");

		storage.deleteAll(userId);

		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(storage.createDownloadUrl(otherUserId, "same-key", Duration.ofMinutes(5)))
				.isNotNull();
	}
}
