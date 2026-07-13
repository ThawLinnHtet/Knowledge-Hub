package com.knowledgehub.api.settings;

import com.knowledgehub.api.chat.ChatProperties;
import com.knowledgehub.api.documents.UploadProperties;
import com.knowledgehub.api.ingestion.IngestionProperties;
import com.knowledgehub.api.search.SearchProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

	private final UploadProperties uploadProperties;
	private final IngestionProperties ingestionProperties;
	private final SearchProperties searchProperties;
	private final ChatProperties chatProperties;

	private final String chatModel;

	SettingsController(
			UploadProperties uploadProperties,
			IngestionProperties ingestionProperties,
			SearchProperties searchProperties,
			ChatProperties chatProperties,
			@Value("${spring.ai.openai.chat.model}") String chatModel) {
		this.uploadProperties = uploadProperties;
		this.ingestionProperties = ingestionProperties;
		this.searchProperties = searchProperties;
		this.chatProperties = chatProperties;
		this.chatModel = chatModel;
	}

	@GetMapping("/system")
	SystemStatus system() {
		return new SystemStatus(
				chatProperties.fakeAi(),
				chatModel,
				ingestionProperties.embeddingModel(),
				ingestionProperties.embeddingDimension(),
				uploadProperties.maxFileSizeBytes(),
				uploadProperties.maxFilesPerBatch(),
				searchProperties.maxResults(),
				chatProperties.maxMessageCharacters());
	}

	public record SystemStatus(
			boolean fakeAi,
			String chatModel,
			String embeddingModel,
			int embeddingDimension,
			long maxUploadSizeBytes,
			int maxFilesPerBatch,
			int maxRetrievedChunks,
			int maxChatMessageCharacters) {}
}
