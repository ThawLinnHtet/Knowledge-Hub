package com.knowledgehub.api.storage;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StorageCleanupJobRepository
		extends JpaRepository<StorageCleanupJobEntity, UUID> {

	@Query(
			value = "select * from storage_cleanup_jobs where not_before <= :now "
					+ "order by not_before, created_at for update skip locked limit 1",
			nativeQuery = true)
	Optional<StorageCleanupJobEntity> findNextDue(@Param("now") Instant now);
}
