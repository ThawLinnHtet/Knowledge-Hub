package com.knowledgehub.api.users;

import java.util.UUID;

public interface AccountDataCleanup {

	void deleteAllOwnedData(UUID userId);
}
