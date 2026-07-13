package com.knowledgehub.api.documents;

import com.knowledgehub.api.collections.CollectionEntity;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.users.UserEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UploadConfirmationTokenService {

	private final UploadConfirmationTokenRepository tokenRepository;
	private final UploadProperties properties;
	private final SecureRandom secureRandom = new SecureRandom();

	@Transactional
	public String create(
			UserEntity user,
			CollectionEntity collection,
			UploadValidator.ValidatedUpload upload) {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant now = Instant.now();
		UploadConfirmationTokenEntity token = new UploadConfirmationTokenEntity();
		token.setUser(user);
		token.setCollection(collection);
		token.setTokenHash(hash(rawToken));
		token.setFileHash(upload.sha256Hash());
		token.setFilename(upload.filename());
		token.setSizeBytes(upload.sizeBytes());
		token.setExpiresAt(now.plus(properties.confirmationTokenTtl()));
		tokenRepository.save(token);
		return rawToken;
	}

	@Transactional
	public Confirmation consume(
			String rawToken,
			UUID userId,
			String fileHash,
			String filename,
			long sizeBytes,
			UUID collectionId) {
		if (rawToken == null || rawToken.isBlank()) {
			throw invalidToken();
		}
		Instant now = Instant.now();
		UploadConfirmationTokenEntity token = tokenRepository.findByTokenHash(hash(rawToken))
				.filter(candidate -> candidate.getUsedAt() == null)
				.filter(candidate -> candidate.getExpiresAt().isAfter(now))
				.filter(candidate -> candidate.getUser().getId().equals(userId))
				.filter(candidate -> candidate.getFileHash().equals(fileHash))
				.filter(candidate -> candidate.getFilename().equals(filename))
				.filter(candidate -> candidate.getSizeBytes() == sizeBytes)
				.filter(candidate -> Objects.equals(collectionId(candidate), collectionId))
				.orElseThrow(this::invalidToken);
		token.setUsedAt(now);
		token.setUpdatedAt(now);
		return new Confirmation(
				token.getUser().getId(),
				collectionId(token),
				token.getFileHash(),
				token.getFilename(),
				token.getSizeBytes());
	}

	private UUID collectionId(UploadConfirmationTokenEntity token) {
		return token.getCollection() == null ? null : token.getCollection().getId();
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
				ErrorCode.UPLOAD_CONFIRMATION_TOKEN_INVALID,
				HttpStatus.BAD_REQUEST,
				"The upload confirmation token is invalid.");
	}

	public record Confirmation(
			UUID userId,
			UUID collectionId,
			String fileHash,
			String filename,
			long sizeBytes) {}
}
