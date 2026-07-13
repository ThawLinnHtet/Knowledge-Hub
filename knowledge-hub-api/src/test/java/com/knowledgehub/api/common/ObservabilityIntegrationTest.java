package com.knowledgehub.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.knowledgehub.api.TestcontainersConfiguration;
import com.knowledgehub.api.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.slf4j.MDC;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class ObservabilityIntegrationTest {

	private final MockMvc mockMvc;

	@Autowired
	ObservabilityIntegrationTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void exposesPublicHealthProbesButProtectsMetrics() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		Logger logger = (Logger) LoggerFactory.getLogger(SecurityConfig.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			mockMvc.perform(get("/actuator/metrics"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
			assertThat(appender.list).hasSize(1);
			assertThat(appender.list.getFirst().getMDCPropertyMap())
					.containsEntry("endpoint", "/actuator/metrics")
					.containsEntry("httpMethod", "GET")
					.containsEntry("httpStatus", "401")
					.containsEntry("errorCode", "AUTHENTICATION_REQUIRED")
					.containsKey("requestId")
					.containsKey("latencyMs");
			assertThat(MDC.get("errorCode")).isNull();
		} finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void exposesOpenApiDocumentationOutsideProduction() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").isNotEmpty())
				.andExpect(jsonPath("$.paths['/api/v1/search']").exists());
	}
}
