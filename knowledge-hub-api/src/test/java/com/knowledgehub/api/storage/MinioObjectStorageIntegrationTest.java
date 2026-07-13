package com.knowledgehub.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinioObjectStorageIntegrationTest {

	private static final String ACCESS_KEY = "kh_test_admin";
	private static final String SECRET_KEY = "kh_test_minio_secret";

	@Container
	private static final GenericContainer<?> MINIO =
			new GenericContainer<>("minio/minio:RELEASE.2025-04-22T22-12-26Z")
					.withEnv("MINIO_ROOT_USER", ACCESS_KEY)
					.withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
					.withCommand("server", "/data")
					.withExposedPorts(9000)
					.waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

	@Test
	void storesPrivatelyPresignsDownloadsAndDeletesIdempotently() throws Exception {
		MinioObjectStorage storage = storage();
		UUID userId = UUID.randomUUID();
		byte[] content = "private knowledge".getBytes(StandardCharsets.UTF_8);

		storage.put(
				userId,
				"documents/opaque",
				new ByteArrayInputStream(content),
				content.length,
				"text/plain");
		try (var stored = storage.get(userId, "documents/opaque")) {
			assertThat(stored.readAllBytes()).isEqualTo(content);
		}

		HttpClient http = HttpClient.newHttpClient();
		URI directUrl = URI.create(endpoint()
				+ "/kh-test-"
				+ userId
				+ "/documents/opaque");
		HttpResponse<String> direct = http.send(
				HttpRequest.newBuilder(directUrl).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(direct.statusCode()).isNotEqualTo(200);

		URI signedUrl = storage.createDownloadUrl(userId, "documents/opaque", Duration.ofMinutes(5));
		HttpResponse<String> downloaded = http.send(
				HttpRequest.newBuilder(signedUrl).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(downloaded.statusCode()).isEqualTo(200);
		assertThat(downloaded.body()).isEqualTo("private knowledge");

		storage.delete(userId, "documents/opaque");
		storage.delete(userId, "documents/opaque");
		assertThatThrownBy(() -> storage.createDownloadUrl(
						userId, "documents/opaque", Duration.ofMinutes(5)))
				.isInstanceOf(ObjectStorageException.class);
	}

	@Test
	void accountCleanupRemovesThePrivateBucketAndAllObjects() {
		MinioObjectStorage storage = storage();
		UUID userId = UUID.randomUUID();
		byte[] content = "content".getBytes(StandardCharsets.UTF_8);
		storage.put(
				userId,
				"documents/one",
				new ByteArrayInputStream(content),
				content.length,
				"text/plain");
		storage.put(
				userId,
				"documents/two",
				new ByteArrayInputStream(content),
				content.length,
				"text/plain");

		storage.deleteAll(userId);
		storage.deleteAll(userId);

		assertThatThrownBy(() -> storage.createDownloadUrl(
						userId, "documents/one", Duration.ofMinutes(5)))
				.isInstanceOf(ObjectStorageException.class);
	}

	private MinioObjectStorage storage() {
		return new MinioObjectStorage(new StorageProperties(
				"minio",
				endpoint(),
				ACCESS_KEY,
				SECRET_KEY,
				"kh-test",
				Duration.ofMinutes(5),
				Duration.ofMinutes(15)));
	}

	private String endpoint() {
		return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
	}
}
