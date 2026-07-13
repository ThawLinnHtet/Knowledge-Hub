package com.knowledgehub.api.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioObjectStorage implements ObjectStorage {

	private final MinioClient client;
	private final StorageProperties properties;

	public MinioObjectStorage(StorageProperties properties) {
		this.properties = properties;
		this.client = MinioClient.builder()
				.endpoint(properties.endpoint())
				.credentials(properties.accessKey(), properties.secretKey())
				.build();
	}

	@Override
	public void put(
			UUID userId, String objectKey, InputStream content, long size, String mediaType) {
		String bucket = bucket(userId);
		try {
			ensureBucket(bucket);
			client.putObject(PutObjectArgs.builder()
					.bucket(bucket)
					.object(objectKey)
					.stream(content, size, -1)
					.contentType(mediaType)
					.build());
		} catch (Exception exception) {
			throw failure("The object could not be stored.", exception);
		}
	}

	@Override
	public InputStream get(UUID userId, String objectKey) {
		try {
			return client.getObject(GetObjectArgs.builder()
					.bucket(bucket(userId))
					.object(objectKey)
					.build());
		} catch (Exception exception) {
			throw failure("The object could not be read.", exception);
		}
	}

	@Override
	public URI createDownloadUrl(UUID userId, String objectKey, Duration ttl) {
		String bucket = bucket(userId);
		try {
			client.statObject(
					StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
			return URI.create(client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
					.method(Method.GET)
					.bucket(bucket)
					.object(objectKey)
					.expiry(Math.toIntExact(ttl.toSeconds()))
					.build()));
		} catch (Exception exception) {
			throw failure("The download URL could not be created.", exception);
		}
	}

	@Override
	public void delete(UUID userId, String objectKey) {
		try {
			client.removeObject(RemoveObjectArgs.builder()
					.bucket(bucket(userId))
					.object(objectKey)
					.build());
		} catch (ErrorResponseException exception) {
			if (!missing(exception)) {
				throw failure("The object could not be deleted.", exception);
			}
		} catch (Exception exception) {
			throw failure("The object could not be deleted.", exception);
		}
	}

	@Override
	public void deleteAll(UUID userId) {
		String bucket = bucket(userId);
		try {
			if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
				return;
			}
			for (var result : client.listObjects(
					ListObjectsArgs.builder().bucket(bucket).recursive(true).build())) {
				client.removeObject(RemoveObjectArgs.builder()
						.bucket(bucket)
						.object(result.get().objectName())
						.build());
			}
			client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
		} catch (ErrorResponseException exception) {
			if (!missing(exception)) {
				throw failure("The user storage could not be deleted.", exception);
			}
		} catch (Exception exception) {
			throw failure("The user storage could not be deleted.", exception);
		}
	}

	private void ensureBucket(String bucket) throws Exception {
		if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
			try {
				client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
			} catch (ErrorResponseException exception) {
				String code = exception.errorResponse().code();
				if (!code.equals("BucketAlreadyOwnedByYou") && !code.equals("BucketAlreadyExists")) {
					throw exception;
				}
			}
		}
	}

	private String bucket(UUID userId) {
		String prefix = properties.bucketPrefix().toLowerCase(Locale.ROOT);
		String value = prefix + "-" + userId.toString();
		if (prefix.isBlank()
				|| value.length() > 63
				|| !value.matches("[a-z0-9][a-z0-9.-]*[a-z0-9]")) {
			throw new IllegalStateException("The MinIO bucket prefix is invalid.");
		}
		return value;
	}

	private boolean missing(ErrorResponseException exception) {
		String code = exception.errorResponse().code();
		return code.equals("NoSuchKey") || code.equals("NoSuchBucket") || code.equals("NotFound");
	}

	private ObjectStorageException failure(String message, Exception cause) {
		return new ObjectStorageException(message, cause);
	}
}
