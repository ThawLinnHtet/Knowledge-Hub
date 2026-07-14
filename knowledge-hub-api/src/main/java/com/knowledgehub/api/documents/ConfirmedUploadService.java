package com.knowledgehub.api.documents;

import com.knowledgehub.api.collections.CollectionEntity;
import com.knowledgehub.api.collections.CollectionRepository;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.common.ErrorResponse;
import com.knowledgehub.api.documents.DocumentDtos.UploadItem;
import com.knowledgehub.api.documents.DocumentUploadController.UploadDecision;
import com.knowledgehub.api.documents.DocumentUploadController.UploadDecisionType;
import com.knowledgehub.api.documents.DocumentUploadController.UploadManifest;
import com.knowledgehub.api.documents.UploadValidator.UploadRejectedException;
import com.knowledgehub.api.storage.ObjectStorageException;
import com.knowledgehub.api.storage.StorageCleanupService;
import com.knowledgehub.api.storage.StorageProperties;
import com.knowledgehub.api.users.AuthenticatedUserService;
import com.knowledgehub.api.users.UserEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ConfirmedUploadService {

	private static final Logger log = LoggerFactory.getLogger(ConfirmedUploadService.class);
	private final AuthenticatedUserService authenticatedUsers;
	private final CollectionRepository collectionRepository;
	private final UploadValidator uploadValidator;
	private final UploadProperties uploadProperties;
	private final ConfirmedUploadItemProcessor itemProcessor;
	private final StorageCleanupService storageCleanupService;
	private final StorageProperties storageProperties;

	public DocumentDtos.UploadResponse upload(
			String email,
			UUID requestedCollectionId,
			List<MultipartFile> files,
			UploadManifest manifest,
			String requestId) {
		validateManifest(files, manifest);
		UserEntity user = authenticatedUsers.requireActive(email);
		CollectionEntity target = requestedCollectionId == null
				? collectionRepository
						.findByUserIdAndUncategorizedTrue(user.getId())
						.orElseThrow(() -> new IllegalStateException(
								"Uncategorized collection is missing."))
				: collectionRepository
						.findByIdAndUserId(requestedCollectionId, user.getId())
						.orElseThrow(() -> new ApiException(
								ErrorCode.RESOURCE_NOT_FOUND,
								HttpStatus.NOT_FOUND,
								"The collection was not found."));

		Map<Integer, UploadDecision> decisions = new HashMap<>();
		manifest.items().forEach(decision -> decisions.put(decision.fileIndex(), decision));
		List<UploadItem> results = new ArrayList<>();
		for (int index = 0; index < files.size(); index++) {
			MultipartFile file = files.get(index);
			UploadDecision decision = decisions.get(index);
			if (decision.decision() == UploadDecisionType.SKIP) {
				results.add(UploadItem.skipped(index, filename(file)));
				continue;
			}
			results.add(uploadOne(
					user.getId(),
					target.getId(),
					requestedCollectionId,
					file,
					decision,
					index,
					requestId));
		}
		return new DocumentDtos.UploadResponse(results);
	}

	private UploadItem uploadOne(
			UUID userId,
			UUID targetCollectionId,
			UUID requestedCollectionId,
			MultipartFile file,
			UploadDecision decision,
			int index,
			String requestId) {
		String objectKey = "documents/" + UUID.randomUUID();
		UUID cleanupJobId = null;
		try {
			var validated = uploadValidator.validate(file);
			cleanupJobId = storageCleanupService.schedule(
					userId, objectKey, storageProperties.orphanCleanupDelay());
			DocumentDtos.DocumentResponse document =
					itemProcessor.upload(
							userId,
							targetCollectionId,
							requestedCollectionId,
							file,
							validated,
							decision.decision(),
							decision.confirmationToken(),
							objectKey);
			cancelCleanup(cleanupJobId);
			return UploadItem.uploaded(
					index,
					document);
		} catch (UploadRejectedException exception) {
			return UploadItem.rejected(
					index, filename(file), error(exception.code(), exception.getMessage(), requestId));
		} catch (ApiException exception) {
			processCleanup(cleanupJobId);
			return UploadItem.rejected(
					index,
					filename(file),
					error(exception.getCode(), exception.getMessage(), requestId));
		} catch (ObjectStorageException exception) {
			processCleanup(cleanupJobId);
			return UploadItem.rejected(
					index,
					filename(file),
					error(ErrorCode.STORAGE_ERROR, "The document could not be stored.", requestId));
		} catch (RuntimeException exception) {
			processCleanup(cleanupJobId);
			log.error("Confirmed upload item failed", exception);
			return UploadItem.rejected(
					index,
					filename(file),
					error(ErrorCode.INTERNAL_ERROR, "The document could not be uploaded.", requestId));
		}
	}

	private void validateManifest(List<MultipartFile> files, UploadManifest manifest) {
		if (files.size() > uploadProperties.maxFilesPerBatch()) {
			throw new ApiException(
					ErrorCode.LIMIT_EXCEEDED,
					HttpStatus.CONTENT_TOO_LARGE,
					"The upload batch contains too many files.",
					Map.of("maxFilesPerBatch", uploadProperties.maxFilesPerBatch()));
		}
		if (files.size() != manifest.items().size()) {
			throw malformedManifest();
		}
		long distinctIndexes = manifest.items().stream()
				.map(UploadDecision::fileIndex)
				.distinct()
				.count();
		boolean outOfRange = manifest.items().stream()
				.anyMatch(item -> item.fileIndex() < 0 || item.fileIndex() >= files.size());
		if (distinctIndexes != files.size() || outOfRange) {
			throw malformedManifest();
		}
	}

	private ApiException malformedManifest() {
		return new ApiException(
				ErrorCode.MALFORMED_REQUEST,
				HttpStatus.BAD_REQUEST,
				"The upload manifest does not match the uploaded files.");
	}

	private ErrorResponse error(ErrorCode code, String message, String requestId) {
		return new ErrorResponse(code.name(), message, requestId, List.of(), Map.of());
	}

	private String filename(MultipartFile file) {
		return file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
	}

	private void processCleanup(UUID cleanupJobId) {
		if (cleanupJobId == null) {
			return;
		}
		try {
			storageCleanupService.processJob(cleanupJobId);
		} catch (RuntimeException exception) {
			log.warn("Could not immediately process storage cleanup job {}", cleanupJobId, exception);
		}
	}

	private void cancelCleanup(UUID cleanupJobId) {
		try {
			storageCleanupService.cancel(cleanupJobId);
		} catch (RuntimeException exception) {
			log.warn("Could not cancel storage cleanup job {}", cleanupJobId, exception);
		}
	}
}
