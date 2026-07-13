package com.knowledgehub.api.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository
		extends JpaRepository<PasswordResetTokenEntity, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

	@Modifying
	@Query("update PasswordResetTokenEntity token set token.usedAt = :now, token.updatedAt = :now "
			+ "where token.user.id = :userId and token.usedAt is null")
	int invalidateUnusedByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
