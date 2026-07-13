package com.knowledgehub.api.auth;

import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.collections.CollectionEntity;
import com.knowledgehub.api.collections.CollectionRepository;
import com.knowledgehub.api.users.UserEntity;
import com.knowledgehub.api.users.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final AuthProperties properties;
	private final CollectionRepository collectionRepository;
	private final SecureRandom secureRandom = new SecureRandom();
	private String dummyPasswordHash;

	@PostConstruct
	void initializeDummyPasswordHash() {
		dummyPasswordHash = passwordEncoder.encode("not-a-user-password");
	}

	@Transactional
	public UserEntity register(String email, String password) {
		if (!properties.registrationEnabled()) {
			throw new ApiException(
					ErrorCode.REGISTRATION_DISABLED, HttpStatus.FORBIDDEN, "Registration is disabled.");
		}
		String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
		if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
			throw emailUnavailable();
		}
		UserEntity user = new UserEntity();
		user.setEmail(normalizedEmail);
		user.setPasswordHash(passwordEncoder.encode(password));
		try {
			userRepository.saveAndFlush(user);
			CollectionEntity fallback = new CollectionEntity();
			fallback.setUser(user);
			fallback.setName("Uncategorized");
			fallback.setUncategorized(true);
			collectionRepository.save(fallback);
			return user;
		} catch (DataIntegrityViolationException exception) {
			throw emailUnavailable();
		}
	}

	@Transactional
	public Tokens login(String email, String password, String sessionName, Map<String, Object> metadata) {
		UserEntity user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
		String passwordHash = user == null ? dummyPasswordHash : user.getPasswordHash();
		boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
		if (user == null || user.getStatus() != UserEntity.Status.ACTIVE || !passwordMatches) {
			throw invalidCredentials();
		}
		return issueTokens(user, sessionName, metadata).tokens();
	}

	@Transactional
	public Tokens refresh(String rawToken) {
		RefreshTokenEntity current = refreshTokenRepository.findByTokenHash(hash(rawToken))
				.filter(token -> token.getRevokedAt() == null)
				.filter(token -> token.getExpiresAt().isAfter(Instant.now()))
				.filter(token -> token.getUser().getStatus() == UserEntity.Status.ACTIVE)
				.orElseThrow(this::invalidRefreshToken);
		current.setRevokedAt(Instant.now());
		current.setLastUsedAt(Instant.now());
		IssuedTokens replacement =
				issueTokens(current.getUser(), current.getSessionName(), current.getDeviceMetadata());
		current.setRotatedToTokenId(replacement.entity().getId());
		return replacement.tokens();
	}

	@Transactional
	public void logout(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
			token.setRevokedAt(Instant.now());
		});
	}

	private IssuedTokens issueTokens(
			UserEntity user, String sessionName, Map<String, Object> metadata) {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		RefreshTokenEntity refreshToken = new RefreshTokenEntity();
		refreshToken.setUser(user);
		refreshToken.setTokenHash(hash(rawToken));
		refreshToken.setExpiresAt(Instant.now().plus(properties.refreshTokenTtl()));
		refreshToken.setSessionName(sessionName);
		refreshToken.setDeviceMetadata(Map.copyOf(metadata));
		refreshTokenRepository.save(refreshToken);
		Tokens tokens =
				new Tokens(jwtTokenProvider.createAccessToken(user), rawToken, properties.refreshTokenTtl());
		return new IssuedTokens(tokens, refreshToken);
	}

	private String hash(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private ApiException invalidCredentials() {
		return new ApiException(
				ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password.");
	}

	private ApiException invalidRefreshToken() {
		return new ApiException(
				ErrorCode.REFRESH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "The refresh token is invalid.");
	}

	private ApiException emailUnavailable() {
		return new ApiException(
				ErrorCode.EMAIL_UNAVAILABLE, HttpStatus.CONFLICT, "The email address is unavailable.");
	}

	public record Tokens(String accessToken, String refreshToken, java.time.Duration refreshTokenTtl) {}

	private record IssuedTokens(Tokens tokens, RefreshTokenEntity entity) {}
}
