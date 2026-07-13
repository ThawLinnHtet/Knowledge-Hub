package com.knowledgehub.api.documents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UploadConfirmationTokenCleanupJobTest {

	@Test
	void removesExpiredAndUsedTokensInOneScheduledOperation() {
		UploadConfirmationTokenRepository repository =
				Mockito.mock(UploadConfirmationTokenRepository.class);
		UploadConfirmationTokenCleanupJob job = new UploadConfirmationTokenCleanupJob(repository);

		job.cleanExpiredAndUsedTokens();

		verify(repository).deleteExpiredOrUsed(any(Instant.class));
	}
}
