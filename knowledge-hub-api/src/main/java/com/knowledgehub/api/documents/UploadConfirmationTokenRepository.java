package com.knowledgehub.api.documents;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadConfirmationTokenRepository
		extends JpaRepository<UploadConfirmationTokenEntity, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<UploadConfirmationTokenEntity> findByTokenHash(String tokenHash);

	@Modifying
	@Query("delete from UploadConfirmationTokenEntity token "
			+ "where token.expiresAt <= :now or token.usedAt is not null")
	int deleteExpiredOrUsed(@Param("now") Instant now);
}
