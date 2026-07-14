package com.knowledgehub.api.collections;

import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.documents.DocumentRepository;
import com.knowledgehub.api.users.AuthenticatedUserService;
import com.knowledgehub.api.users.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionService {

	private final AuthenticatedUserService authenticatedUsers;
	private final CollectionRepository collectionRepository;
	private final DocumentRepository documentRepository;
	private final CollectionMapper collectionMapper;

	@Transactional(readOnly = true)
	public List<CollectionController.CollectionResponse> list(String email) {
		UserEntity user = authenticatedUsers.requireActive(email);
		List<CollectionEntity> collections =
				collectionRepository.findByUserIdOrderByUncategorizedDescNameAsc(user.getId());
		Map<UUID, DocumentRepository.CollectionDocumentCount> counts = documentRepository
				.countByCollectionIds(collections.stream().map(CollectionEntity::getId).toList())
				.stream()
				.collect(Collectors.toMap(
						DocumentRepository.CollectionDocumentCount::getCollectionId,
						Function.identity()));
		return collections.stream()
				.map(collection -> collectionMapper.toResponse(
						collection,
						counts.containsKey(collection.getId())
								? counts.get(collection.getId()).getDocumentCount()
								: 0))
				.toList();
	}

	@Transactional
	public CollectionController.CollectionResponse create(String email, String requestedName) {
		UserEntity user = authenticatedUsers.requireActive(email);
		String name = requestedName.trim();
		if (collectionRepository.existsByUserIdAndNameIgnoreCase(user.getId(), name)) {
			throw nameUnavailable();
		}
		CollectionEntity collection = new CollectionEntity();
		collection.setUser(user);
		collection.setName(name);
		try {
			collectionRepository.saveAndFlush(collection);
		} catch (DataIntegrityViolationException exception) {
			throw nameUnavailable();
		}
		return collectionMapper.toResponse(collection, 0);
	}

	@Transactional
	public CollectionController.CollectionResponse rename(
			String email, UUID collectionId, String requestedName) {
		UserEntity user = authenticatedUsers.requireActive(email);
		CollectionEntity collection = requireOwnedWithLock(collectionId, user.getId());
		protectUncategorized(collection);
		String name = requestedName.trim();
		if (!collection.getName().equalsIgnoreCase(name)
				&& collectionRepository.existsByUserIdAndNameIgnoreCase(user.getId(), name)) {
			throw nameUnavailable();
		}
		collection.setName(name);
		collection.setUpdatedAt(Instant.now());
		try {
			collectionRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw nameUnavailable();
		}
		return collectionMapper.toResponse(
				collection, documentRepository.countByCollectionId(collection.getId()));
	}

	@Transactional
	public void delete(String email, UUID collectionId) {
		UserEntity user = authenticatedUsers.requireActive(email);
		CollectionEntity collection = requireOwnedWithLock(collectionId, user.getId());
		protectUncategorized(collection);
		CollectionEntity fallback = collectionRepository
				.findByUserIdAndUncategorizedTrue(user.getId())
				.orElseThrow(() -> new IllegalStateException("Uncategorized collection is missing."));
		documentRepository.moveToCollection(user.getId(), collection.getId(), fallback, Instant.now());
		collectionRepository.delete(collection);
	}

	private CollectionEntity requireOwnedWithLock(UUID collectionId, UUID userId) {
		return collectionRepository
				.findWithLockByIdAndUserId(collectionId, userId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.RESOURCE_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"The collection was not found."));
	}

	private void protectUncategorized(CollectionEntity collection) {
		if (collection.isUncategorized()) {
			throw new ApiException(
					ErrorCode.UNCATEGORIZED_COLLECTION_PROTECTED,
					HttpStatus.CONFLICT,
					"The Uncategorized collection cannot be changed or deleted.");
		}
	}

	private ApiException nameUnavailable() {
		return new ApiException(
				ErrorCode.COLLECTION_NAME_UNAVAILABLE,
				HttpStatus.CONFLICT,
				"The collection name is unavailable.");
	}
}
