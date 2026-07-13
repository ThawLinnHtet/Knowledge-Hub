package com.knowledgehub.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Request-ID";
	public static final String REQUEST_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
	public static final String REQUEST_STARTED_ATTRIBUTE =
			RequestIdFilter.class.getName() + ".startedNanos";
	private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
		request.setAttribute(REQUEST_ATTRIBUTE, requestId);
		request.setAttribute(REQUEST_STARTED_ATTRIBUTE, System.nanoTime());
		response.setHeader(HEADER_NAME, requestId);
		MDC.put("requestId", requestId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove("requestId");
		}
	}

	private String resolveRequestId(String candidate) {
		if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
			return candidate;
		}
		return UUID.randomUUID().toString();
	}
}
