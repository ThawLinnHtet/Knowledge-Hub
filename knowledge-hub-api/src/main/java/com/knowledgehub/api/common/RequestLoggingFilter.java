package com.knowledgehub.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long started = System.nanoTime();
		put("requestId", request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
		put("endpoint", request.getRequestURI());
		put("httpMethod", request.getMethod());
		put("userId", userId());
		try {
			filterChain.doFilter(request, response);
		} finally {
			put("httpStatus", response.getStatus());
			put("latencyMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
			log.info("request completed");
			MDC.remove("endpoint");
			MDC.remove("httpMethod");
			MDC.remove("httpStatus");
			MDC.remove("latencyMs");
			MDC.remove("userId");
			MDC.remove("errorCode");
		}
	}

	private String userId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwt) {
			return jwt.getToken().getClaimAsString("uid");
		}
		return null;
	}

	private void put(String key, Object value) {
		if (value != null) MDC.put(key, value.toString());
	}
}
