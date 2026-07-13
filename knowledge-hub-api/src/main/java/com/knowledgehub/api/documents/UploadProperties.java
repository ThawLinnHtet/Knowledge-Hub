package com.knowledgehub.api.documents;

import java.time.Duration;
import java.util.Set;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.upload")
@Validated
public record UploadProperties(
		@NotEmpty Set<String> allowedExtensions,
		@NotEmpty Set<String> allowedMimeTypes,
		@Positive long maxFileSizeBytes,
		@Positive int maxFilesPerBatch,
		@NotNull Duration confirmationTokenTtl,
		@NotNull Duration confirmationTokenCleanupDelay) {

	public UploadProperties {
		if (confirmationTokenTtl != null && !confirmationTokenTtl.isPositive()) {
			throw new IllegalArgumentException("Confirmation token TTL must be positive.");
		}
		if (confirmationTokenCleanupDelay != null
				&& !confirmationTokenCleanupDelay.isPositive()) {
			throw new IllegalArgumentException("Confirmation token cleanup delay must be positive.");
		}
	}
}
