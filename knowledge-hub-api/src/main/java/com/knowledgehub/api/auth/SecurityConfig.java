package com.knowledgehub.api.auth;

import com.knowledgehub.api.common.ErrorCode;
import com.knowledgehub.api.common.RequestIdFilter;
import com.knowledgehub.api.users.UserEntity;
import com.knowledgehub.api.users.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@Slf4j
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties, UserRepository userRepository) {
		SecretKey key = new SecretKeySpec(
				properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		var issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
		var audience = (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>) jwt ->
				jwt.getAudience().contains(properties.audience())
						? OAuth2TokenValidatorResult.success()
						: OAuth2TokenValidatorResult.failure(
								new OAuth2Error("invalid_token", "Required audience is missing", null));
		var activeUser = (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>) jwt -> {
			try {
				java.util.UUID userId = java.util.UUID.fromString(jwt.getClaimAsString("uid"));
				return userRepository.existsByIdAndStatus(userId, UserEntity.Status.ACTIVE)
						? OAuth2TokenValidatorResult.success()
						: OAuth2TokenValidatorResult.failure(
								new OAuth2Error("invalid_token", "User is not active", null));
			} catch (RuntimeException exception) {
				return OAuth2TokenValidatorResult.failure(
						new OAuth2Error("invalid_token", "User claim is invalid", null));
			}
		};
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience, activeUser));
		return decoder;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		return http.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
						.requireCsrfProtectionMatcher(request ->
								"POST".equals(request.getMethod())
										&& ("/api/v1/auth/refresh".equals(request.getRequestURI())
												|| "/api/v1/auth/logout".equals(request.getRequestURI()))))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/api/v1/auth/**",
								"/actuator/health/**",
								"/v3/api-docs/**",
								"/swagger-ui/**",
								"/swagger-ui.html")
						.permitAll()
						.anyRequest()
						.authenticated())
				.oauth2ResourceServer(oauth -> oauth
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint((request, response, exception) ->
								writeError(response, request, 401, ErrorCode.AUTHENTICATION_REQUIRED)))
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) ->
								writeError(response, request, 401, ErrorCode.AUTHENTICATION_REQUIRED))
						.accessDeniedHandler((request, response, exception) ->
								writeError(response, request, 403, ErrorCode.ACCESS_DENIED)))
				.build();
	}

	private void writeError(
			jakarta.servlet.http.HttpServletResponse response,
			jakarta.servlet.http.HttpServletRequest request,
			int status,
			ErrorCode code)
			throws java.io.IOException {
		Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
		put("endpoint", request.getRequestURI());
		put("httpMethod", request.getMethod());
		put("httpStatus", status);
		put("errorCode", code.name());
		put("userId", userId());
		try {
			String message = status == 401 ? "Authentication is required." : "Access is denied.";
			response.setStatus(status);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().printf(
					"{\"code\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\",\"fieldErrors\":[],\"metadata\":{}}",
					code.name(), message, requestId == null ? "unknown" : requestId);
		} finally {
			Object started = request.getAttribute(RequestIdFilter.REQUEST_STARTED_ATTRIBUTE);
			if (started instanceof Long startedNanos) {
				put("latencyMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
			}
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
