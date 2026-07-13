package com.knowledgehub.api.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private static final String REFRESH_COOKIE = "refresh_token";
	private final AuthService authService;
	private final AuthProperties properties;
	private final JwtProperties jwtProperties;
	private final AuthRateLimiter rateLimiter;
	private final PasswordResetService passwordResetService;

	@PostMapping("/register")
	ResponseEntity<UserResponse> register(
			@Valid @RequestBody Credentials request, HttpServletRequest servletRequest) {
		rateLimiter.check("register", servletRequest.getRemoteAddr(), request.email());
		var user = authService.register(request.email(), request.password());
		return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(user.getId(), user.getEmail()));
	}

	@PostMapping("/login")
	ResponseEntity<TokenResponse> login(
			@Valid @RequestBody Credentials request, HttpServletRequest servletRequest) {
		rateLimiter.check("login", servletRequest.getRemoteAddr(), request.email());
		String userAgent = java.util.Optional.ofNullable(servletRequest.getHeader("User-Agent"))
				.orElse("Unknown device");
		String sessionName = userAgent.substring(0, Math.min(userAgent.length(), 255));
		return tokenResponse(authService.login(
				request.email(),
				request.password(),
				sessionName,
				Map.of("ip", servletRequest.getRemoteAddr(), "userAgent", sessionName)));
	}

	@PostMapping("/refresh")
	ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
		rateLimiter.check("refresh", request.getRemoteAddr(), null);
		return tokenResponse(authService.refresh(refreshToken(request)));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request) {
		authService.logout(optionalRefreshToken(request));
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
				.build();
	}

	@GetMapping("/csrf")
	Map<String, String> csrf(CsrfToken csrfToken) {
		return Map.of("token", csrfToken.getToken(), "headerName", csrfToken.getHeaderName());
	}

	@PostMapping("/forgot-password")
	ResponseEntity<Void> forgotPassword(
			@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
		rateLimiter.check("forgot-password", servletRequest.getRemoteAddr(), request.email());
		passwordResetService.requestReset(request.email(), servletRequest.getRemoteAddr());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/reset-password")
	ResponseEntity<Void> resetPassword(
			@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
		rateLimiter.check("reset-password", servletRequest.getRemoteAddr(), null);
		passwordResetService.resetPassword(request.token(), request.password());
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<TokenResponse> tokenResponse(AuthService.Tokens tokens) {
		TokenResponse body = new TokenResponse(
				tokens.accessToken(), "Bearer", jwtProperties.accessTokenTtl().toSeconds());
		return ResponseEntity.ok()
				.header(
						HttpHeaders.SET_COOKIE,
						refreshCookie(tokens.refreshToken(), tokens.refreshTokenTtl()).toString())
				.body(body);
	}

	private ResponseCookie refreshCookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
				.httpOnly(true)
				.secure(properties.secureCookies())
				.sameSite("Lax")
				.path("/api/v1/auth")
				.maxAge(maxAge)
				.build();
	}

	private String refreshToken(HttpServletRequest request) {
		String token = optionalRefreshToken(request);
		if (token == null || token.isBlank()) {
			throw new com.knowledgehub.api.common.ApiException(
					com.knowledgehub.api.common.ErrorCode.REFRESH_TOKEN_INVALID,
					HttpStatus.UNAUTHORIZED,
					"The refresh token is invalid.");
		}
		return token;
	}

	private String optionalRefreshToken(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (REFRESH_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public record Credentials(
			@NotBlank @Email @Size(max = 320) String email,
			@NotBlank @Size(min = 12, max = 128) String password) {}

	public record UserResponse(java.util.UUID id, String email) {}

	public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {}

	public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {}

	public record ResetPasswordRequest(
			@NotBlank String token, @NotBlank @Size(min = 12, max = 128) String password) {}
}
