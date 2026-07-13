package com.knowledgehub.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RequestLoggingFilterTest {

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void logsSafeRequestMetadataAndAuthenticatedUserId() throws Exception {
		UUID userId = UUID.randomUUID();
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("user@example.com")
				.claim("uid", userId.toString())
				.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
		request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, "request-123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		new RequestLoggingFilter().doFilter(request, response, (ignoredRequest, actualResponse) ->
				((jakarta.servlet.http.HttpServletResponse) actualResponse).setStatus(202));

		assertThat(appender.list).hasSize(1);
		ILoggingEvent event = appender.list.getFirst();
		assertThat(event.getFormattedMessage()).isEqualTo("request completed");
		assertThat(event.getMDCPropertyMap())
				.containsEntry("requestId", "request-123")
				.containsEntry("userId", userId.toString())
				.containsEntry("endpoint", "/api/v1/documents")
				.containsEntry("httpMethod", "GET")
				.containsEntry("httpStatus", "202")
				.containsKey("latencyMs");
		logger.detachAppender(appender);
	}
}
