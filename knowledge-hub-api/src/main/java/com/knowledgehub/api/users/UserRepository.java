package com.knowledgehub.api.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	Optional<UserEntity> findByEmailIgnoreCase(String email);

	boolean existsByIdAndStatus(UUID id, UserEntity.Status status);
}
