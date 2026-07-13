package com.knowledgehub.api.auth;

import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.users.UserEntity;
import com.knowledgehub.api.users.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository tokenRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailService mailService;
	private final AuthProperties properties;
	private final SecureRandom secureRandom = new SecureRandom();

	@Transactional
	public void requestReset(String email, String requestedIp) {
		userRepository.findByEmailIgnoreCase(email.trim())
				.filter(user -> user.getStatus() == UserEntity.Status.ACTIVE)
				.ifPresent(user -> createToken(user, requestedIp));
	}

	@Transactional
	public void resetPassword(String rawToken, String password) {
		Instant now = Instant.now();
		PasswordResetTokenEntity token = tokenRepository.findByTokenHash(hash(rawToken))
				.filter(candidate -> candidate.getUsedAt() == null)
				.filter(candidate -> candidate.getExpiresAt().isAfter(now))
				.filter(candidate -> candidate.getUser().getStatus() == UserEntity.Status.ACTIVE)
				.orElseThrow(this::invalidToken);
		token.setUsedAt(now);
		token.setUpdatedAt(now);
		UserEntity user = token.getUser();
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setUpdatedAt(now);
		refreshTokenRepository.revokeAllByUserId(user.getId(), now);
	}

	private void createToken(UserEntity user, String requestedIp) {
		Instant now = Instant.now();
		tokenRepository.invalidateUnusedByUserId(user.getId(), now);
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		PasswordResetTokenEntity token = new PasswordResetTokenEntity();
		token.setUser(user);
		token.setTokenHash(hash(rawToken));
		token.setExpiresAt(now.plus(properties.resetTokenTtl()));
		token.setRequestedIp(requestedIp);
		tokenRepository.save(token);
		mailService.sendPasswordReset(user.getEmail(), rawToken);
	}

	private String hash(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private ApiException invalidToken() {
		return new ApiException(
				ErrorCode.RESET_TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "The reset token is invalid.");
	}
}
