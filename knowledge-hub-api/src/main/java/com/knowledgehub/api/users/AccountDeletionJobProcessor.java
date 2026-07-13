package com.knowledgehub.api.users;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountDeletionJobProcessor {

	private final AccountDeletionTransactions transactions;
	private final AccountDataCleanup accountDataCleanup;

	@Scheduled(
			initialDelayString = "${app.account-deletion.initial-delay:30s}",
			fixedDelayString = "${app.account-deletion.poll-delay:30s}")
	public void processNext() {
		Instant now = Instant.now();
		transactions.claimNext(now).ifPresent(job -> process(job, now));
	}

	private void process(AccountDeletionTransactions.ClaimedJob job, Instant now) {
		try {
			accountDataCleanup.deleteAllOwnedData(job.userId());
		} catch (RuntimeException exception) {
			transactions.recordFailure(job.jobId(), now, safeMessage(exception));
		}
	}

	private String safeMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return message.substring(0, Math.min(message.length(), 1000));
	}
}
