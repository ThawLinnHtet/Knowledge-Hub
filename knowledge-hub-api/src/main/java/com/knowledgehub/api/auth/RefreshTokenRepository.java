package com.knowledgehub.api.auth;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

	@Modifying(flushAutomatically = true)
	@Query("update RefreshTokenEntity token set token.revokedAt = :now, token.updatedAt = :now "
			+ "where token.user.id = :userId and token.revokedAt is null")
	int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") java.time.Instant now);
}
