package com.knowledgehub.api.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.knowledgehub.api.collections.CollectionEntity;
import com.knowledgehub.api.common.ApiException;
import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.users.UserEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UploadConfirmationTokenServiceTest {

	@Mock
	private UploadConfirmationTokenRepository repository;

	private UploadConfirmationTokenService service;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new UploadConfirmationTokenService(
				repository,
				new UploadProperties(
						Set.of(), Set.of(), 100, 20, Duration.ofMinutes(15), Duration.ofHours(1)));
	}

	@Test
	void createsHashedTokenBoundToTheExactUpload() {
		UserEntity user = user();
		CollectionEntity collection = collection(user);
		UploadValidator.ValidatedUpload upload =
				new UploadValidator.ValidatedUpload("notes.txt", "txt", "text/plain", 12, "file-hash");
		ArgumentCaptor<UploadConfirmationTokenEntity> captor =
				ArgumentCaptor.forClass(UploadConfirmationTokenEntity.class);
		when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		Instant before = Instant.now();
		String rawToken = service.create(user, collection, upload);

		UploadConfirmationTokenEntity stored = captor.getValue();
		assertThat(rawToken).isNotBlank().doesNotContain(stored.getTokenHash());
		assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(rawToken);
		assertThat(stored.getUser()).isSameAs(user);
		assertThat(stored.getCollection()).isSameAs(collection);
		assertThat(stored.getFileHash()).isEqualTo("file-hash");
		assertThat(stored.getFilename()).isEqualTo("notes.txt");
		assertThat(stored.getSizeBytes()).isEqualTo(12);
		assertThat(stored.getExpiresAt()).isAfterOrEqualTo(before.plus(Duration.ofMinutes(15)));
	}

	@Test
	void consumesMatchingTokenOnlyOnceAndRejectsChangedBinding() {
		UserEntity user = user();
		CollectionEntity collection = collection(user);
		UploadConfirmationTokenEntity token = token(user, collection);
		when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

		UploadConfirmationTokenService.Confirmation confirmation = service.consume(
				"raw-token", user.getId(), "file-hash", "notes.txt", 12, collection.getId());

		assertThat(confirmation.userId()).isEqualTo(user.getId());
		assertThat(confirmation.collectionId()).isEqualTo(collection.getId());
		assertThat(token.getUsedAt()).isNotNull();
		assertInvalid(() -> service.consume(
				"raw-token", user.getId(), "file-hash", "notes.txt", 12, collection.getId()));

		token.setUsedAt(null);
		assertInvalid(() -> service.consume(
				"raw-token", user.getId(), "different-hash", "notes.txt", 12, collection.getId()));
		token.setExpiresAt(Instant.now().minusSeconds(1));
		assertInvalid(() -> service.consume(
				"raw-token", user.getId(), "file-hash", "notes.txt", 12, collection.getId()));
	}

	private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
		assertThatThrownBy(action)
				.isInstanceOf(ApiException.class)
				.satisfies(exception -> assertThat(((ApiException) exception).getCode())
						.isEqualTo(ErrorCode.UPLOAD_CONFIRMATION_TOKEN_INVALID));
	}

	private UserEntity user() {
		UserEntity user = new UserEntity();
		user.setId(UUID.randomUUID());
		return user;
	}

	private CollectionEntity collection(UserEntity user) {
		CollectionEntity collection = new CollectionEntity();
		collection.setId(UUID.randomUUID());
		collection.setUser(user);
		return collection;
	}

	private UploadConfirmationTokenEntity token(UserEntity user, CollectionEntity collection) {
		UploadConfirmationTokenEntity token = new UploadConfirmationTokenEntity();
		token.setUser(user);
		token.setCollection(collection);
		token.setFileHash("file-hash");
		token.setFilename("notes.txt");
		token.setSizeBytes(12);
		token.setExpiresAt(Instant.now().plusSeconds(60));
		return token;
	}
}
