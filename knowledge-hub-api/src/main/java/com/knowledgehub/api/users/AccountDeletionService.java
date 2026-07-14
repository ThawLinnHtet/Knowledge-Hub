package com.knowledgehub.api.users;

import com.knowledgehub.api.auth.RefreshTokenRepository;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {

	private final AuthenticatedUserService authenticatedUsers;
	private final RefreshTokenRepository refreshTokenRepository;
	private final AccountDeletionJobRepository jobRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public void requestDeletion(String email, String password, String confirmation) {
		if (!"DELETE".equals(confirmation)) {
			throw new ApiException(
					ErrorCode.ACCOUNT_DELETION_CONFIRMATION_INVALID,
					HttpStatus.BAD_REQUEST,
					"Type DELETE exactly to confirm account deletion.");
		}
		UserEntity user;
		try {
			user = authenticatedUsers.requireActiveForUpdate(email);
		} catch (ApiException exception) {
			throw invalidCredentials();
		}
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw invalidCredentials();
		}

		Instant now = Instant.now();
		user.setStatus(UserEntity.Status.DELETION_PENDING);
		user.setDeletionRequestedAt(now);
		user.setUpdatedAt(now);
		refreshTokenRepository.revokeAllByUserId(user.getId(), now);

		AccountDeletionJobEntity job = new AccountDeletionJobEntity();
		job.setUser(user);
		jobRepository.save(job);
	}

	private ApiException invalidCredentials() {
		return new ApiException(
				ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password.");
	}
}
