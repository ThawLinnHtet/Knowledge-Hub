package com.knowledgehub.api.users;

import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {

	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public UserEntity requireActive(String principalName) {
		return userRepository
				.findByIdAndStatus(userId(principalName), UserEntity.Status.ACTIVE)
				.orElseThrow(this::unauthorized);
	}

	@Transactional
	public UserEntity requireActiveForUpdate(String principalName) {
		return userRepository
				.findWithLockByIdAndStatus(userId(principalName), UserEntity.Status.ACTIVE)
				.orElseThrow(this::unauthorized);
	}

	public UUID userId(String principalName) {
		try {
			return UUID.fromString(principalName);
		} catch (RuntimeException exception) {
			throw unauthorized();
		}
	}

	private ApiException unauthorized() {
		return new ApiException(
				ErrorCode.AUTHENTICATION_REQUIRED,
				HttpStatus.UNAUTHORIZED,
				"Authentication is required.");
	}
}
