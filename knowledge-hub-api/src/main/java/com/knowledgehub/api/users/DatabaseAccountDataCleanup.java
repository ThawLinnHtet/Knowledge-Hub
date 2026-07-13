package com.knowledgehub.api.users;

import com.knowledgehub.api.storage.ObjectStorage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseAccountDataCleanup implements AccountDataCleanup {

	private final ObjectStorage objectStorage;
	private final AccountDeletionTransactions transactions;

	@Override
	public void deleteAllOwnedData(UUID userId) {
		objectStorage.deleteAll(userId);
		transactions.deleteUser(userId);
	}
}
