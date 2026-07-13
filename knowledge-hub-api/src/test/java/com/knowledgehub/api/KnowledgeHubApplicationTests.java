package com.knowledgehub.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class KnowledgeHubApplicationTests {

	@Value("${spring.application.name}")
	private String applicationName;

	@Test
	void contextLoadsWithKnowledgeHubIdentity() {
		assertThat(applicationName).isEqualTo("knowledge-hub-api");
	}
}
