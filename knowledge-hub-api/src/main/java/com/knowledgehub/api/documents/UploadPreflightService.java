package com.knowledgehub.api.documents;

import com.knowledgehub.api.collections.CollectionEntity;
import com.knowledgehub.api.collections.CollectionRepository;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.common.ErrorResponse;
import com.knowledgehub.api.documents.UploadValidator.UploadRejectedException;
import com.knowledgehub.api.documents.UploadValidator.ValidatedUpload;
import com.knowledgehub.api.users.UserEntity;
import com.knowledgehub.api.users.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UploadPreflightService {

	private final UserRepository userRepository;
	private final CollectionRepository collectionRepository;
	private final DocumentRepository documentRepository;
	private final UploadValidator uploadValidator;
	private final UploadConfirmationTokenService confirmationTokenService;
	private final UploadProperties uploadProperties;

	public PreflightResponse preflight(
			String email, UUID collectionId, List<MultipartFile> files, String requestId) {
		if (files.size() > uploadProperties.maxFilesPerBatch()) {
			throw new ApiException(
					ErrorCode.LIMIT_EXCEEDED,
					HttpStatus.CONTENT_TOO_LARGE,
					"The upload batch contains too many files.",
					Map.of("maxFilesPerBatch", uploadProperties.maxFilesPerBatch()));
		}
		UserEntity user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new ApiException(
						ErrorCode.AUTHENTICATION_REQUIRED,
						HttpStatus.UNAUTHORIZED,
						"Authentication is required."));
		CollectionEntity collection = collectionId == null
				? null
				: collectionRepository
						.findByIdAndUserId(collectionId, user.getId())
						.orElseThrow(() -> new ApiException(
								ErrorCode.RESOURCE_NOT_FOUND,
								HttpStatus.NOT_FOUND,
								"The collection was not found."));

		List<PreflightItem> items = new ArrayList<>();
		Set<String> batchHashes = new HashSet<>();
		for (MultipartFile file : files) {
			items.add(preflightFile(user, collection, file, requestId, batchHashes));
		}
		return new PreflightResponse(items);
	}

	private PreflightItem preflightFile(
			UserEntity user,
			CollectionEntity collection,
			MultipartFile file,
			String requestId,
			Set<String> batchHashes) {
		try {
			ValidatedUpload upload = uploadValidator.validate(file);
			boolean repeatedInBatch = !batchHashes.add(upload.sha256Hash());
			if (repeatedInBatch
					|| documentRepository.existsByUserIdAndSha256Hash(
							user.getId(), upload.sha256Hash())) {
				String token = confirmationTokenService.create(user, collection, upload);
				return PreflightItem.duplicate(upload, token);
			}
			return PreflightItem.accepted(upload);
		} catch (UploadRejectedException exception) {
			String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
			ErrorResponse error = new ErrorResponse(
					exception.code().name(),
					exception.getMessage(),
					requestId,
					List.of(),
					Map.of("filename", filename));
			return PreflightItem.rejected(filename, file.getSize(), error);
		}
	}

	public record PreflightResponse(List<PreflightItem> items) {}

	public record PreflightItem(
			String filename,
			String status,
			long sizeBytes,
			String detectedMediaType,
			String sha256Hash,
			String confirmationToken,
			ErrorResponse error) {

		static PreflightItem accepted(ValidatedUpload upload) {
			return from(upload, "ACCEPTED", null);
		}

		static PreflightItem duplicate(ValidatedUpload upload, String token) {
			return from(upload, "DUPLICATE", token);
		}

		static PreflightItem rejected(String filename, long sizeBytes, ErrorResponse error) {
			return new PreflightItem(filename, "REJECTED", sizeBytes, null, null, null, error);
		}

		private static PreflightItem from(ValidatedUpload upload, String status, String token) {
			return new PreflightItem(
					upload.filename(),
					status,
					upload.sizeBytes(),
					upload.detectedMediaType(),
					upload.sha256Hash(),
					token,
					null);
		}
	}
}
