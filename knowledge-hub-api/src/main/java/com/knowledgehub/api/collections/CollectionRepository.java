package com.knowledgehub.api.collections;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CollectionRepository extends JpaRepository<CollectionEntity, UUID> {

	Optional<CollectionEntity> findByIdAndUserId(UUID id, UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<CollectionEntity> findWithLockByIdAndUserId(UUID id, UUID userId);

	Optional<CollectionEntity> findByUserIdAndUncategorizedTrue(UUID userId);

	List<CollectionEntity> findByUserIdOrderByUncategorizedDescNameAsc(UUID userId);

	boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);
}
