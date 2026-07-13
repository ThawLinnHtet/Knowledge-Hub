package com.knowledgehub.api.auth;

import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimiter {

	private static final int MAX_TRACKED_KEYS = 10_000;
	private final AuthProperties properties;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public AuthRateLimiter(AuthProperties properties) {
		this.properties = properties;
	}

	public void check(String action, String ipAddress, String account) {
		checkKey(action + ":ip:" + ipAddress);
		if (account != null && !account.isBlank()) {
			checkKey(action + ":account:" + account.toLowerCase(java.util.Locale.ROOT));
		}
	}

	private void checkKey(String key) {
		Instant now = Instant.now();
		if (windows.size() >= MAX_TRACKED_KEYS && !windows.containsKey(key)) {
			windows.entrySet().removeIf(
					entry -> entry.getValue().started.plus(properties.rateLimitWindow()).isBefore(now));
			if (windows.size() >= MAX_TRACKED_KEYS) {
				throw rateLimited();
			}
		}
		Window window = windows.compute(key, (ignored, current) -> {
			if (current == null || current.started.plus(properties.rateLimitWindow()).isBefore(now)) {
				return new Window(now, 1);
			}
			return new Window(current.started, current.attempts + 1);
		});
		if (window.attempts > properties.rateLimitMaxAttempts()) {
			throw rateLimited();
		}
	}

	private ApiException rateLimited() {
		return new ApiException(
				ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, "Too many authentication attempts.");
	}

	private record Window(Instant started, int attempts) {}
}
