package com.knowledgehub.api.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountDeletionJobProcessorTest {

	private final AccountDeletionTransactions transactions = mock(AccountDeletionTransactions.class);
	private final AccountDataCleanup cleanup = mock(AccountDataCleanup.class);
	private final AccountDeletionJobProcessor processor =
			new AccountDeletionJobProcessor(transactions, cleanup);

	@Test
	void transientCleanupFailureSchedulesRetry() {
		AccountDeletionTransactions.ClaimedJob job = claimedJob();
		when(transactions.claimNext(any())).thenReturn(Optional.of(job));
		doThrow(new IllegalStateException("storage unavailable"))
				.when(cleanup)
				.deleteAllOwnedData(job.userId());

		processor.processNext();

		verify(transactions).recordFailure(
				org.mockito.ArgumentMatchers.eq(job.jobId()),
				any(),
				org.mockito.ArgumentMatchers.eq("storage unavailable"));
	}

	@Test
	void cleanupFailureKeepsRetryingWithCappedBackoff() {
		AccountDeletionJobRepository repository = mock(AccountDeletionJobRepository.class);
		AccountDeletionJobEntity job = pendingJob(4);
		when(repository.findById(job.getId())).thenReturn(Optional.of(job));
		AccountDeletionTransactions transactionService =
				new AccountDeletionTransactions(repository, mock(UserRepository.class));
		java.time.Instant now = java.time.Instant.now();
		transactionService.recordFailure(job.getId(), now, "storage unavailable");
		assertThat(job.getStatus()).isEqualTo(AccountDeletionJobEntity.Status.PENDING);
		assertThat(job.getRetryCount()).isEqualTo(5);
		assertThat(job.getNextRetryAt()).isAfter(now);
	}

	private AccountDeletionTransactions.ClaimedJob claimedJob() {
		return new AccountDeletionTransactions.ClaimedJob(UUID.randomUUID(), UUID.randomUUID());
	}

	private AccountDeletionJobEntity pendingJob(int retryCount) {
		UserEntity user = new UserEntity();
		user.setId(UUID.randomUUID());
		AccountDeletionJobEntity job = new AccountDeletionJobEntity();
		job.setId(UUID.randomUUID());
		job.setUser(user);
		job.setRetryCount(retryCount);
		return job;
	}
}
