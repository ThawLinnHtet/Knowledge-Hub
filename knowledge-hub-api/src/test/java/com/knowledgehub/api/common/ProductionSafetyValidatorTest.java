package com.knowledgehub.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionSafetyValidatorTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(ProductionSafetyValidator.class)
			.withPropertyValues("spring.profiles.active=prod");

	@Test
	void productionRefusesDevelopmentDefaults() {
		contextRunner
				.withPropertyValues(
						"spring.datasource.url=jdbc:postgresql://localhost:5433/knowledgehub",
						"spring.datasource.username=kh_user",
						"spring.datasource.password=kh_dev_secret",
						"spring.mail.host=",
						"spring.ai.model.chat=none",
						"spring.ai.model.embedding=none",
						"app.base-url=http://localhost:8080",
						"app.web-url=http://localhost:5173",
						"app.cors.allowed-origins=http://localhost:5173",
						"app.storage.endpoint=http://localhost:9000",
						"app.storage.access-key=kh_admin",
						"app.storage.secret-key=kh_dev_minio_secret",
						"app.jwt.secret=change-this-development-secret-32-bytes",
						"app.auth.secure-cookies=false",
						"app.auth.log-reset-tokens=true",
						"app.ingestion.fake-ai=true",
						"app.chat.fake-ai=true")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasMessageContaining("Production configuration is unsafe");
				});
	}

	@Test
	void productionAcceptsExplicitExternalConfiguration() {
		contextRunner
				.withPropertyValues(safeProductionProperties())
				.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void productionRefusesInMemoryObjectStorage() {
		contextRunner
				.withPropertyValues(safeProductionProperties())
				.withPropertyValues("app.storage.type=fake")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasMessageContaining("app.storage.type");
				});
	}

	@Test
	void productionRefusesObjectStorageRootCredentials() {
		contextRunner
				.withPropertyValues(safeProductionProperties())
				.withPropertyValues(
						"MINIO_ROOT_USER=application-key",
						"MINIO_ROOT_PASSWORD=application-secret")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasMessageContaining("app.storage.access-key")
							.hasMessageContaining("app.storage.secret-key");
				});
	}

	@Test
	void productionRefusesCleartextExternalEndpoints() {
		contextRunner
				.withPropertyValues(
						"spring.datasource.url=jdbc:postgresql://database.internal/knowledgehub",
						"spring.datasource.username=knowledge_app",
						"spring.datasource.password=database-secret",
						"spring.mail.host=smtp.internal",
						"spring.mail.properties.mail.smtp.starttls.enable=false",
						"spring.mail.properties.mail.smtp.starttls.required=false",
						"spring.ai.openai.base-url=http://provider.example/v1",
						"spring.ai.openai.api-key=provider-key",
						"spring.ai.model.chat=openai",
						"spring.ai.model.embedding=openai",
						"app.base-url=http://api.knowledge.example",
						"app.web-url=https://knowledge.example",
						"app.cors.allowed-origins=https://knowledge.example",
						"app.storage.endpoint=https://objects.knowledge.example",
						"app.storage.access-key=application-key",
						"app.storage.secret-key=application-secret",
						"app.jwt.secret=a-production-secret-with-at-least-32-bytes",
						"app.auth.secure-cookies=true",
						"app.auth.log-reset-tokens=false",
						"app.ingestion.fake-ai=false",
						"app.chat.fake-ai=false",
						"REGISTRATION_ENABLED=false")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasMessageContaining("spring.datasource.url")
							.hasMessageContaining("spring.ai.openai.base-url")
							.hasMessageContaining("spring.mail.properties.mail.smtp.starttls.enable")
							.hasMessageContaining("spring.mail.properties.mail.smtp.starttls.required");
				});
	}

	@Test
	void productionRefusesConflictingJdbcTlsOptions() {
		contextRunner
				.withPropertyValues(
						"spring.datasource.url=jdbc:postgresql://database.internal/knowledgehub?sslmode=verify-full&sslmode=disable",
						"spring.datasource.username=knowledge_app",
						"spring.datasource.password=database-secret",
						"spring.mail.host=smtp.internal",
						"spring.mail.properties.mail.smtp.starttls.enable=true",
						"spring.mail.properties.mail.smtp.starttls.required=true",
						"spring.ai.openai.base-url=https://provider.example/v1",
						"spring.ai.openai.api-key=provider-key",
						"spring.ai.model.chat=openai",
						"spring.ai.model.embedding=openai",
						"app.base-url=https://api.knowledge.example",
						"app.web-url=https://knowledge.example",
						"app.cors.allowed-origins=https://knowledge.example",
						"app.storage.endpoint=https://objects.knowledge.example",
						"app.storage.access-key=application-key",
						"app.storage.secret-key=application-secret",
						"app.jwt.secret=a-production-secret-with-at-least-32-bytes",
						"app.auth.secure-cookies=true",
						"app.auth.log-reset-tokens=false",
						"app.ingestion.fake-ai=false",
						"app.chat.fake-ai=false",
						"REGISTRATION_ENABLED=false")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasMessageContaining("spring.datasource.url");
				});
	}

	private String[] safeProductionProperties() {
		return new String[] {
			"spring.datasource.url=jdbc:postgresql://database.internal/knowledgehub?sslmode=verify-full",
			"spring.datasource.username=knowledge_app",
			"spring.datasource.password=database-secret",
			"spring.mail.host=smtp.internal",
			"spring.mail.properties.mail.smtp.starttls.enable=true",
			"spring.mail.properties.mail.smtp.starttls.required=true",
			"spring.ai.openai.base-url=https://provider.example/v1",
			"spring.ai.openai.api-key=provider-key",
			"spring.ai.model.chat=openai",
			"spring.ai.model.embedding=openai",
			"app.base-url=https://api.knowledge.example",
			"app.web-url=https://knowledge.example",
			"app.cors.allowed-origins=https://knowledge.example",
			"app.storage.type=minio",
			"app.storage.endpoint=https://objects.knowledge.example",
			"app.storage.access-key=application-key",
			"app.storage.secret-key=application-secret",
			"app.jwt.secret=a-production-secret-with-at-least-32-bytes",
			"app.auth.secure-cookies=true",
			"app.auth.log-reset-tokens=false",
			"app.ingestion.fake-ai=false",
			"app.chat.fake-ai=false",
			"REGISTRATION_ENABLED=false"
		};
	}
}
