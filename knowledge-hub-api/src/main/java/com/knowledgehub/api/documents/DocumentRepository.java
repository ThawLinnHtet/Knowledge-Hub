package com.knowledgehub.api.documents;

import com.knowledgehub.api.collections.CollectionEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

	boolean existsByUserIdAndSha256Hash(UUID userId, String sha256Hash);

	boolean existsByObjectKey(String objectKey);

	long countByCollectionId(UUID collectionId);

	interface CollectionDocumentCount {

		UUID getCollectionId();

		long getDocumentCount();
	}

	@Query("select document.collection.id as collectionId, count(document) as documentCount "
			+ "from DocumentEntity document where document.collection.id in :collectionIds "
			+ "group by document.collection.id")
	java.util.List<CollectionDocumentCount> countByCollectionIds(
			@Param("collectionIds") java.util.Collection<UUID> collectionIds);

	@EntityGraph(attributePaths = "collection")
	Optional<DocumentEntity> findByIdAndUserId(UUID id, UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "collection")
	Optional<DocumentEntity> findWithLockByIdAndUserId(UUID id, UUID userId);

	@EntityGraph(attributePaths = "collection")
	@Query(
			value = "select document from DocumentEntity document "
					+ "where document.user.id = :userId "
					+ "and (:status is null or document.status = :status) "
					+ "and (:collectionId is null or document.collection.id = :collectionId) "
					+ "and (:fileExtension is null or document.fileExtension = :fileExtension) "
					+ "and document.createdAt >= coalesce(:uploadedFrom, document.createdAt) "
					+ "and document.createdAt <= coalesce(:uploadedTo, document.createdAt)",
			countQuery = "select count(document) from DocumentEntity document "
					+ "where document.user.id = :userId "
					+ "and (:status is null or document.status = :status) "
					+ "and (:collectionId is null or document.collection.id = :collectionId) "
					+ "and (:fileExtension is null or document.fileExtension = :fileExtension) "
					+ "and document.createdAt >= coalesce(:uploadedFrom, document.createdAt) "
					+ "and document.createdAt <= coalesce(:uploadedTo, document.createdAt)")
	Page<DocumentEntity> findOwned(
			@Param("userId") UUID userId,
			@Param("status") DocumentEntity.Status status,
			@Param("collectionId") UUID collectionId,
			@Param("fileExtension") String fileExtension,
			@Param("uploadedFrom") Instant uploadedFrom,
			@Param("uploadedTo") Instant uploadedTo,
			Pageable pageable);

	@Modifying
	@Query("update DocumentEntity document "
			+ "set document.collection = :target, document.updatedAt = :now "
			+ "where document.user.id = :userId and document.collection.id = :sourceId")
	int moveToCollection(
			@Param("userId") UUID userId,
			@Param("sourceId") UUID sourceId,
			@Param("target") CollectionEntity target,
			@Param("now") Instant now);
}
