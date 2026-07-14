package com.knowledgehub.api.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgehub.api.collections.CollectionEntity;
import com.knowledgehub.api.collections.CollectionRepository;
import com.knowledgehub.api.documents.DocumentUploadController.UploadDecision;
import com.knowledgehub.api.documents.DocumentUploadController.UploadDecisionType;
import com.knowledgehub.api.documents.DocumentUploadController.UploadManifest;
import com.knowledgehub.api.storage.StorageCleanupService;
import com.knowledgehub.api.storage.StorageProperties;
import com.knowledgehub.api.users.AuthenticatedUserService;
import com.knowledgehub.api.users.UserEntity;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ConfirmedUploadServiceTest {

	@Test
	void returnsUploadedWhenCleanupCancellationFailsAfterDocumentCommit() {
		UUID userId = UUID.randomUUID();
		UUID collectionId = UUID.randomUUID();
		UUID cleanupJobId = UUID.randomUUID();
		UserEntity user = new UserEntity();
		user.setId(userId);
		CollectionEntity collection = new CollectionEntity();
		collection.setId(collectionId);
		AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
		CollectionRepository collectionRepository = mock(CollectionRepository.class);
		UploadValidator uploadValidator = mock(UploadValidator.class);
		ConfirmedUploadItemProcessor itemProcessor = mock(ConfirmedUploadItemProcessor.class);
		StorageCleanupService cleanupService = mock(StorageCleanupService.class);
		UploadProperties uploadProperties = new UploadProperties(
				Set.of("txt"),
				Set.of("text/plain"),
				1024,
				10,
				Duration.ofMinutes(5),
				Duration.ofMinutes(1));
		StorageProperties storageProperties = new StorageProperties(
				"fake",
				"http://localhost",
				"access",
				"secret",
				"knowledge-hub",
				Duration.ofMinutes(5),
				Duration.ofMinutes(1));
		MockMultipartFile file = new MockMultipartFile(
				"files", "notes.txt", "text/plain", "notes".getBytes());
		var validated = new UploadValidator.ValidatedUpload(
				"notes.txt", "txt", "text/plain", 5, "a".repeat(64));
		var document = new DocumentDtos.DocumentResponse(
				UUID.randomUUID(),
				"notes.txt",
				"PENDING",
				new DocumentDtos.CollectionRef(collectionId, "Uncategorized"),
				"text/plain",
				"txt",
				5,
				null,
				null,
				null,
				0,
				false,
				null,
				null,
				null,
				null);

		when(authenticatedUsers.requireActive("owner-id")).thenReturn(user);
		when(collectionRepository.findByUserIdAndUncategorizedTrue(userId))
				.thenReturn(Optional.of(collection));
		when(uploadValidator.validate(file)).thenReturn(validated);
		when(cleanupService.schedule(any(), any(), any())).thenReturn(cleanupJobId);
		when(itemProcessor.upload(any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(document);
		doThrow(new IllegalStateException("database unavailable"))
				.when(cleanupService)
				.cancel(cleanupJobId);
		ConfirmedUploadService service = new ConfirmedUploadService(
				authenticatedUsers,
				collectionRepository,
				uploadValidator,
				uploadProperties,
				itemProcessor,
				cleanupService,
				storageProperties);

		var response = service.upload(
				"owner-id",
				null,
				List.of(file),
				new UploadManifest(List.of(new UploadDecision(0, UploadDecisionType.UPLOAD, null))),
				"request-id");

		assertThat(response.items()).singleElement().satisfies(item -> {
			assertThat(item.status()).isEqualTo("UPLOADED");
			assertThat(item.document()).isEqualTo(document);
		});
		verify(cleanupService, never()).processJob(cleanupJobId);
	}
}
