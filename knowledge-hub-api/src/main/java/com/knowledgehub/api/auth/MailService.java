package com.knowledgehub.api.auth;

public interface MailService {

	void sendPasswordReset(String email, String rawToken);
}
