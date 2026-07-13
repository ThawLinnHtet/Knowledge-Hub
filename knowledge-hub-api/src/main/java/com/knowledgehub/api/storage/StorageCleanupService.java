package com.knowledgehub.api.storage;

import com.knowledgehub.api.documents.DocumentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StorageCleanupService {

	private final StorageCleanupJobRepository cleanupJobRepository;
	private final DocumentRepository documentRepository;
	private final ObjectStorage objectStorage;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public UUID schedule(UUID userId, String objectKey, Duration delay) {
		StorageCleanupJobEntity job = new StorageCleanupJobEntity();
		job.setUserId(userId);
		job.setObjectKey(objectKey);
		job.setNotBefore(Instant.now().plus(delay));
		cleanupJobRepository.saveAndFlush(job);
		return job.getId();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void cancel(UUID jobId) {
		cleanupJobRepository.deleteById(jobId);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processJob(UUID jobId) {
		cleanupJobRepository.findById(jobId).ifPresent(this::process);
	}

	@Scheduled(fixedDelayString = "${app.storage.cleanup-poll-delay:1m}")
	@Transactional
	public void processNext() {
		cleanupJobRepository.findNextDue(Instant.now()).ifPresent(this::process);
	}

	private void process(StorageCleanupJobEntity job) {
		if (documentRepository.existsByObjectKey(job.getObjectKey())) {
			cleanupJobRepository.delete(job);
			return;
		}
		try {
			objectStorage.delete(job.getUserId(), job.getObjectKey());
			cleanupJobRepository.delete(job);
		} catch (ObjectStorageException exception) {
			job.setAttempts(job.getAttempts() + 1);
			job.setLastError(safeMessage(exception));
			job.setNotBefore(Instant.now().plus(Duration.ofMinutes(Math.min(job.getAttempts(), 60))));
			job.setUpdatedAt(Instant.now());
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
