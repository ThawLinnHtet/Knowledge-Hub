package com.knowledgehub.api.ingestion;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionWorker {

	private final IngestionTransactions transactions;
	private final IngestionProcessor processor;

	@Scheduled(
			initialDelayString = "${app.ingestion.initial-delay:30s}",
			fixedDelayString = "${app.ingestion.poll-delay:5s}")
	public void processNext() {
		transactions.claimNext(Instant.now()).ifPresent(processor::process);
	}
}
