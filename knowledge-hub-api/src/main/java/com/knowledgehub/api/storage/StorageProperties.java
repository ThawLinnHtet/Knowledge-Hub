package com.knowledgehub.api.storage;

import java.time.Duration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.storage")
@Validated
public record StorageProperties(
		@NotBlank String type,
		@NotBlank String endpoint,
		@NotBlank String accessKey,
		@NotBlank String secretKey,
		@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9.-]{0,24}[a-z0-9]") String bucketPrefix,
		@NotNull Duration downloadUrlTtl,
		@NotNull Duration orphanCleanupDelay) {

	public StorageProperties {
		if (downloadUrlTtl != null
				&& (!downloadUrlTtl.isPositive() || downloadUrlTtl.compareTo(Duration.ofDays(7)) > 0)) {
			throw new IllegalArgumentException("Download URL TTL must be between one second and seven days.");
		}
		if (orphanCleanupDelay != null && !orphanCleanupDelay.isPositive()) {
			throw new IllegalArgumentException("Orphan cleanup delay must be positive.");
		}
	}
}
