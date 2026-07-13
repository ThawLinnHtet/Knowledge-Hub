package com.knowledgehub.api.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
		boolean registrationEnabled,
		Duration refreshTokenTtl,
		Duration resetTokenTtl,
		boolean secureCookies,
		int rateLimitMaxAttempts,
		Duration rateLimitWindow) {}
