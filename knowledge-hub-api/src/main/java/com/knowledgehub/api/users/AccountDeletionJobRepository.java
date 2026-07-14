package com.knowledgehub.api.users;

import java.util.UUID;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountDeletionJobRepository
		extends JpaRepository<AccountDeletionJobEntity, UUID> {

	@Query(
			value = "select * from account_deletion_jobs "
					+ "where (status in ('PENDING', 'FAILED') "
					+ "and (next_retry_at is null or next_retry_at <= :now)) "
					+ "or (status = 'PROCESSING' and lock_expires_at < :now) "
					+ "order by created_at for update skip locked limit 1",
			nativeQuery = true)
	Optional<AccountDeletionJobEntity> findNextEligible(@Param("now") Instant now);
}
