package com.knowledgehub.api.search;

import com.knowledgehub.api.search.SearchDtos.Mode;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;

	@GetMapping
	SearchDtos.SearchResponse search(
			@RequestParam("q") String query,
			@RequestParam(defaultValue = "HYBRID") Mode mode,
			@RequestParam(required = false) UUID collectionId,
			@RequestParam(name = "documentId", required = false) List<UUID> documentIds,
			@RequestParam(required = false) String fileExtension,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
					Instant uploadedFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
					Instant uploadedTo,
			@RequestParam(defaultValue = "10") int limit,
			Principal principal) {
		return searchService.search(
				principal.getName(),
				query,
				mode,
				collectionId,
				documentIds,
				fileExtension,
				uploadedFrom,
				uploadedTo,
				limit);
	}
}
