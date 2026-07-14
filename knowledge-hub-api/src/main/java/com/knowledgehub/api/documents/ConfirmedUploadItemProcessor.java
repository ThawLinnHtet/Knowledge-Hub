package com.knowledgehub.api.documents;

import com.knowledgehub.api.collections.CollectionEntity;
import com.knowledgehub.api.collections.CollectionRepository;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.documents.DocumentUploadController.UploadDecisionType;
import com.knowledgehub.api.documents.UploadValidator.ValidatedUpload;
import com.knowledgehub.api.storage.ObjectStorage;
import com.knowledgehub.api.storage.ObjectStorageException;
import com.knowledgehub.api.users.AuthenticatedUserService;
import com.knowledgehub.api.users.UserEntity;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ConfirmedUploadItemProcessor {

	private final AuthenticatedUserService authenticatedUsers;
	private final CollectionRepository collectionRepository;
	private final DocumentRepository documentRepository;
	private final UploadConfirmationTokenService confirmationTokenService;
	private final ObjectStorage objectStorage;
	private final DocumentMapper documentMapper;
	private final JdbcTemplate jdbcTemplate;

	@Transactional
	public DocumentDtos.DocumentResponse upload(
			UUID userId,
			UUID targetCollectionId,
			UUID requestedCollectionId,
			MultipartFile file,
			ValidatedUpload upload,
			UploadDecisionType decision,
			String confirmationToken,
			String objectKey) {
		UserEntity user = authenticatedUsers.requireActiveForUpdate(userId.toString());
		CollectionEntity collection = collectionRepository
				.findByIdAndUserId(targetCollectionId, userId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.RESOURCE_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"The collection was not found."));
		lockDuplicateDecision(userId, upload.sha256Hash());
		boolean duplicate =
				documentRepository.existsByUserIdAndSha256Hash(userId, upload.sha256Hash());
		if (duplicate && decision != UploadDecisionType.UPLOAD_DUPLICATE) {
			throw new ApiException(
					ErrorCode.DUPLICATE_UPLOAD,
					HttpStatus.CONFLICT,
					"The document already exists and requires duplicate confirmation.");
		}
		if (decision == UploadDecisionType.UPLOAD_DUPLICATE) {
			confirmationTokenService.consume(
					confirmationToken,
					userId,
					upload.sha256Hash(),
					upload.filename(),
					upload.sizeBytes(),
					requestedCollectionId);
		}

		try (InputStream input = file.getInputStream()) {
			objectStorage.put(
					userId,
					objectKey,
					input,
					upload.sizeBytes(),
					upload.detectedMediaType());
		} catch (IOException exception) {
			throw new ObjectStorageException("The upload content could not be read.", exception);
		}

		DocumentEntity document = new DocumentEntity();
		document.setUser(user);
		document.setCollection(collection);
		document.setOriginalFilename(upload.filename());
		document.setObjectKey(objectKey);
		document.setMediaType(upload.detectedMediaType());
		document.setFileExtension(upload.extension());
		document.setSizeBytes(upload.sizeBytes());
		document.setSha256Hash(upload.sha256Hash());
		documentRepository.saveAndFlush(document);
		return documentMapper.toResponse(document);
	}

	private void lockDuplicateDecision(UUID userId, String sha256Hash) {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			try (var statement =
					connection.prepareStatement("select pg_advisory_xact_lock(?, ?)")) {
				statement.setInt(1, userId.hashCode());
				statement.setInt(2, sha256Hash.hashCode());
				statement.execute();
				return null;
			}
		});
	}
}
