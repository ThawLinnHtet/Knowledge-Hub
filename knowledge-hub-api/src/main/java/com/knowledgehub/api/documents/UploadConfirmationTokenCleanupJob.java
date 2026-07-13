package com.knowledgehub.api.documents;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UploadConfirmationTokenCleanupJob {

	private final UploadConfirmationTokenRepository tokenRepository;

	@Scheduled(fixedDelayString = "${app.upload.confirmation-token-cleanup-delay:1h}")
	@Transactional
	public void cleanExpiredAndUsedTokens() {
		tokenRepository.deleteExpiredOrUsed(Instant.now());
	}
}
