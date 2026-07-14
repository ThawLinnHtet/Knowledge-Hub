package com.knowledgehub.api.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSafetyValidator implements InitializingBean {

	private static final String DEVELOPMENT_JWT_SECRET =
			"change-this-development-secret-32-bytes";
	private final Environment environment;

	public ProductionSafetyValidator(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void afterPropertiesSet() {
		List<String> unsafe = new ArrayList<>();
		requireExternalUrl("spring.datasource.url", unsafe);
		requireJdbcTls("spring.datasource.url", unsafe);
		requireHttpsUrl("app.base-url", unsafe);
		requireHttpsUrl("app.web-url", unsafe);
		requireValue("app.storage.type", "minio", unsafe);
		requireHttpsUrl("app.storage.endpoint", unsafe);
		requireHttpsUrl("spring.ai.openai.base-url", unsafe);
		requireSecret("spring.datasource.username", "kh_user", unsafe);
		requireSecret("spring.datasource.password", "kh_dev_secret", unsafe);
		for (String origin : environment.getProperty("app.cors.allowed-origins", "").split(",")) {
			if (!isExternalHttpsUrl(origin.strip())) unsafe.add("app.cors.allowed-origins");
		}
		requireNonBlank("spring.mail.host", unsafe);
		requireEnabled("spring.mail.properties.mail.smtp.starttls.enable", unsafe);
		requireEnabled("spring.mail.properties.mail.smtp.starttls.required", unsafe);
		requireNonBlank("spring.ai.openai.api-key", unsafe);
		requireValue("spring.ai.model.chat", "openai", unsafe);
		requireValue("spring.ai.model.embedding", "openai", unsafe);
		requireSecret("app.storage.access-key", "kh_admin", unsafe);
		requireSecret("app.storage.secret-key", "kh_dev_minio_secret", unsafe);
		requireDifferent("app.storage.access-key", "MINIO_ROOT_USER", unsafe);
		requireDifferent("app.storage.secret-key", "MINIO_ROOT_PASSWORD", unsafe);
		if (!environment.containsProperty("REGISTRATION_ENABLED")) {
			unsafe.add("REGISTRATION_ENABLED");
		}
		if (DEVELOPMENT_JWT_SECRET.equals(environment.getProperty("app.jwt.secret"))) {
			unsafe.add("app.jwt.secret");
		}
		if (!environment.getProperty("app.auth.secure-cookies", Boolean.class, false)) {
			unsafe.add("app.auth.secure-cookies");
		}
		if (environment.getProperty("app.auth.log-reset-tokens", Boolean.class, false)) {
			unsafe.add("app.auth.log-reset-tokens");
		}
		if (environment.getProperty("app.ingestion.fake-ai", Boolean.class, true)) {
			unsafe.add("app.ingestion.fake-ai");
		}
		if (environment.getProperty("app.chat.fake-ai", Boolean.class, true)) {
			unsafe.add("app.chat.fake-ai");
		}
		if (!unsafe.isEmpty()) {
			throw new IllegalStateException(
					"Production configuration is unsafe: " + String.join(", ", unsafe));
		}
	}

	private void requireExternalUrl(String property, List<String> unsafe) {
		String value = environment.getProperty(property);
		if (value == null || value.isBlank() || isLocalUrl(value)) unsafe.add(property);
	}

	private void requireHttpsUrl(String property, List<String> unsafe) {
		String value = environment.getProperty(property);
		if (value == null || value.isBlank() || !isExternalHttpsUrl(value)) unsafe.add(property);
	}

	private void requireJdbcTls(String property, List<String> unsafe) {
		String value = environment.getProperty(property, "");
		try {
			String query = URI.create(value.startsWith("jdbc:") ? value.substring(5) : value).getQuery();
			if (query == null) {
				unsafe.add(property);
				return;
			}
			List<String> tlsOptions = List.of(query.split("&")).stream()
					.filter(option -> option.startsWith("sslmode=") || option.startsWith("ssl="))
					.toList();
			if (!tlsOptions.equals(List.of("sslmode=verify-full"))) unsafe.add(property);
		} catch (IllegalArgumentException exception) {
			unsafe.add(property);
		}
	}

	private void requireNonBlank(String property, List<String> unsafe) {
		String value = environment.getProperty(property);
		if (value == null || value.isBlank()) unsafe.add(property);
	}

	private void requireValue(String property, String expected, List<String> unsafe) {
		if (!expected.equals(environment.getProperty(property))) unsafe.add(property);
	}

	private void requireEnabled(String property, List<String> unsafe) {
		if (!environment.getProperty(property, Boolean.class, false)) unsafe.add(property);
	}

	private void requireSecret(String property, String developmentValue, List<String> unsafe) {
		String value = environment.getProperty(property);
		if (value == null || value.isBlank() || developmentValue.equals(value)) unsafe.add(property);
	}

	private void requireDifferent(String property, String otherProperty, List<String> unsafe) {
		String value = environment.getProperty(property);
		String otherValue = environment.getProperty(otherProperty);
		if (value != null && !value.isBlank() && value.equals(otherValue)) unsafe.add(property);
	}

	private boolean isLocalUrl(String value) {
		try {
			String normalized = value.startsWith("jdbc:") ? value.substring(5) : value;
			String host = URI.create(normalized).getHost();
			return host == null
					|| "localhost".equalsIgnoreCase(host)
					|| "127.0.0.1".equals(host)
					|| "::1".equals(host);
		} catch (IllegalArgumentException exception) {
			return true;
		}
	}

	private boolean isExternalHttpsUrl(String value) {
		try {
			URI uri = URI.create(value);
			return "https".equalsIgnoreCase(uri.getScheme()) && !isLocalUrl(value);
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
