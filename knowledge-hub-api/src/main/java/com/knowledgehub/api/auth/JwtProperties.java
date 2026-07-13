package com.knowledgehub.api.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(
		String issuer, String audience, Duration accessTokenTtl, String secret) {

	public JwtProperties {
		if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("app.jwt.secret must contain at least 32 bytes");
		}
	}
}
