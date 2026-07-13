package com.knowledgehub.api.users;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDeletionTransactions {

	private static final int MAX_RETRIES = 5;
	private final AccountDeletionJobRepository jobRepository;
	private final UserRepository userRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<ClaimedJob> claimNext(Instant now) {
		return jobRepository.findNextEligible(now).map(job -> {
			job.setStatus(AccountDeletionJobEntity.Status.PROCESSING);
			job.setLockedAt(now);
			job.setLockExpiresAt(now.plus(Duration.ofMinutes(5)));
			jobRepository.saveAndFlush(job);
			return new ClaimedJob(job.getId(), job.getUser().getId());
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(UUID jobId, Instant now, String failureMessage) {
		jobRepository.findById(jobId).ifPresent(job -> {
			job.setRetryCount(job.getRetryCount() + 1);
			job.setFailureMessage(failureMessage);
			job.setLockedAt(null);
			job.setLockExpiresAt(null);
			if (job.getRetryCount() >= MAX_RETRIES) {
				job.setStatus(AccountDeletionJobEntity.Status.FAILED);
			} else {
				job.setStatus(AccountDeletionJobEntity.Status.PENDING);
				job.setNextRetryAt(now.plus(Duration.ofMinutes(job.getRetryCount())));
			}
			job.setUpdatedAt(Instant.now());
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteUser(UUID userId) {
		userRepository.deleteById(userId);
		userRepository.flush();
	}

	public record ClaimedJob(UUID jobId, UUID userId) {}
}
