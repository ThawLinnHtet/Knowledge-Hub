package com.knowledgehub.api.users;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	Optional<UserEntity> findByEmailIgnoreCase(String email);

	Optional<UserEntity> findByIdAndStatus(UUID id, UserEntity.Status status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<UserEntity> findWithLockByIdAndStatus(UUID id, UserEntity.Status status);

	boolean existsByIdAndStatus(UUID id, UserEntity.Status status);
}
