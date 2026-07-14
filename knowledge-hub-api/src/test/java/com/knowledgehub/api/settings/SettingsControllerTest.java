package com.knowledgehub.api.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.knowledgehub.api.chat.ChatProperties;
import com.knowledgehub.api.documents.UploadProperties;
import com.knowledgehub.api.ingestion.IngestionProperties;
import com.knowledgehub.api.search.SearchProperties;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettingsControllerTest {

	@Test
	void exposesOnlySafeReadOnlyModelAndLimitStatus() {
		UploadProperties upload = new UploadProperties(
				Set.of("pdf", "txt"), Set.of("application/pdf", "text/plain"),
				52_428_800, 20, Duration.ofMinutes(15), Duration.ofHours(1));
		IngestionProperties ingestion = new IngestionProperties(
				100_000, 500, 1_000, 200, 1_024, "embedding-model", true,
				Duration.ofMinutes(5), 5, Duration.ofMinutes(1));
		SearchProperties search = new SearchProperties(10, 500, 0.1);
		ChatProperties chat = new ChatProperties(
				true, 4_000, 50, 12, 4_096, 10, 0.01, 0.7, Duration.ofMinutes(2), Duration.ofSeconds(10));
		SettingsController controller = new SettingsController(
				upload, ingestion, search, chat, "google/gemini-2.5-flash-lite");

		SettingsController.SystemStatus response = controller.system();

		assertThat(response.fakeAi()).isTrue();
		assertThat(response.chatModel()).isEqualTo("google/gemini-2.5-flash-lite");
		assertThat(response.embeddingModel()).isEqualTo("embedding-model");
		assertThat(response.embeddingDimension()).isEqualTo(1_024);
		assertThat(response.maxUploadSizeBytes()).isEqualTo(52_428_800);
		assertThat(response.maxFilesPerBatch()).isEqualTo(20);
		assertThat(response.maxRetrievedChunks()).isEqualTo(10);
		assertThat(response.maxChatMessageCharacters()).isEqualTo(4_000);
	}

	@Test
	void rejectsInvalidChatStreamTiming() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new ChatProperties(
						true, 4_000, 50, 12, 4_096, 10, 0.01, 0.7,
						Duration.ofMinutes(2), Duration.ofNanos(1)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new ChatProperties(
						true, 4_000, 50, 12, 4_096, 10, 0.01, 0.7,
						Duration.ofSeconds(10), Duration.ofSeconds(10)));
	}
}
