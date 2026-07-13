package com.knowledgehub.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class ConfiguredMailService implements MailService {

	private static final Logger log = LoggerFactory.getLogger(ConfiguredMailService.class);
	private final ObjectProvider<JavaMailSender> mailSender;

	public ConfiguredMailService(ObjectProvider<JavaMailSender> mailSender) {
		this.mailSender = mailSender;
	}

	@Value("${app.web-url}")
	private String webUrl;

	@Value("${app.auth.log-reset-tokens:false}")
	private boolean logResetTokens;

	@Override
	public void sendPasswordReset(String email, String rawToken) {
		String link = webUrl + "/reset-password?token="
				+ java.net.URLEncoder.encode(rawToken, java.nio.charset.StandardCharsets.UTF_8);
		if (logResetTokens) {
			log.warn("Development password reset link for {}: {}", email, link);
			return;
		}
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			throw new IllegalStateException(
					"SMTP is not configured and development reset-token logging is disabled");
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(email);
		message.setSubject("Reset your Knowledge Hub password");
		message.setText("Use this single-use link to reset your password: " + link);
		sender.send(message);
	}
}
