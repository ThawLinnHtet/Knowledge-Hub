package com.knowledgehub.api.documents;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

	private final DocumentService documentService;

	@GetMapping
	DocumentDtos.DocumentPage list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) DocumentEntity.Status status,
			@RequestParam(required = false) UUID collectionId,
			@RequestParam(required = false) String fileExtension,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
					Instant uploadedFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
					Instant uploadedTo,
			Principal principal) {
		return documentService.list(
				principal.getName(),
				page,
				size,
				status,
				collectionId,
				fileExtension,
				uploadedFrom,
				uploadedTo);
	}

	@GetMapping("/{id}")
	DocumentDtos.DocumentDetailResponse detail(@PathVariable UUID id, Principal principal) {
		return documentService.detail(principal.getName(), id);
	}

	@PostMapping("/{id}/download-url")
	DocumentDtos.DownloadUrlResponse downloadUrl(@PathVariable UUID id, Principal principal) {
		return documentService.downloadUrl(principal.getName(), id);
	}

	@PostMapping("/{id}/retry")
	ResponseEntity<DocumentDtos.DocumentResponse> retry(
			@PathVariable UUID id, Principal principal) {
		return ResponseEntity.accepted().body(documentService.retry(principal.getName(), id));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
		documentService.delete(principal.getName(), id);
		return ResponseEntity.noContent().build();
	}
}
