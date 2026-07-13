package com.knowledgehub.api.collections;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
public class CollectionController {

	private final CollectionService collectionService;

	@GetMapping
	List<CollectionResponse> list(Principal principal) {
		return collectionService.list(principal.getName());
	}

	@PostMapping
	ResponseEntity<CollectionResponse> create(
			@Valid @RequestBody CollectionRequest request, Principal principal) {
		CollectionResponse response = collectionService.create(principal.getName(), request.name());
		return ResponseEntity.created(URI.create("/api/v1/collections/" + response.id()))
				.body(response);
	}

	@PatchMapping("/{id}")
	CollectionResponse rename(
			@PathVariable UUID id,
			@Valid @RequestBody CollectionRequest request,
			Principal principal) {
		return collectionService.rename(principal.getName(), id, request.name());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
		collectionService.delete(principal.getName(), id);
		return ResponseEntity.noContent().build();
	}

	public record CollectionRequest(@NotBlank @Size(max = 160) String name) {}

	public record CollectionResponse(
			UUID id,
			String name,
			boolean uncategorized,
			long documentCount,
			Instant createdAt,
			Instant updatedAt) {}
}
