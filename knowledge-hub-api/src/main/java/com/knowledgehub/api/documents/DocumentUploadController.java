package com.knowledgehub.api.documents;

import com.knowledgehub.api.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents/uploads")
@RequiredArgsConstructor
public class DocumentUploadController {

	private final UploadPreflightService preflightService;
	private final ConfirmedUploadService confirmedUploadService;

	@PostMapping(value = "/preflight", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	UploadPreflightService.PreflightResponse preflight(
			@RequestParam("files") List<MultipartFile> files,
			@RequestParam(required = false) UUID collectionId,
			Principal principal,
			HttpServletRequest request) {
		return preflightService.preflight(
				principal.getName(),
				collectionId,
				files,
				(String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	DocumentDtos.UploadResponse upload(
			@RequestParam("files") List<MultipartFile> files,
			@RequestPart("manifest") @Valid UploadManifest manifest,
			@RequestParam(required = false) UUID collectionId,
			Principal principal,
			HttpServletRequest request) {
		return confirmedUploadService.upload(
				principal.getName(),
				collectionId,
				files,
				manifest,
				(String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
	}

	public record UploadManifest(@NotEmpty List<@NotNull @Valid UploadDecision> items) {}

	public record UploadDecision(
			int fileIndex, @NotNull UploadDecisionType decision, String confirmationToken) {}

	public enum UploadDecisionType {
		UPLOAD,
		UPLOAD_DUPLICATE,
		SKIP
	}
}
