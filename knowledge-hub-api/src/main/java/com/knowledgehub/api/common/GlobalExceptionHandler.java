package com.knowledgehub.api.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApiException(
			ApiException exception, HttpServletRequest request) {
		return response(
				exception.getStatus(),
				exception.getCode(),
				exception.getMessage(),
				List.of(),
				exception.getMetadata(),
				request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(
			MethodArgumentNotValidException exception, HttpServletRequest request) {
		List<ErrorResponse.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new ErrorResponse.FieldError(
						error.getField(),
						error.getCode() == null ? "Invalid" : error.getCode(),
						error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage()))
				.toList();
		return response(
				HttpStatus.BAD_REQUEST,
				ErrorCode.VALIDATION_FAILED,
				"One or more fields are invalid.",
				fieldErrors,
				Map.of(),
				request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleMalformedRequest(HttpServletRequest request) {
		return response(
				HttpStatus.BAD_REQUEST,
				ErrorCode.MALFORMED_REQUEST,
				"The request body is malformed.",
				List.of(),
				Map.of(),
				request);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> handleMultipartLimit(HttpServletRequest request) {
		return response(
				HttpStatus.CONTENT_TOO_LARGE,
				ErrorCode.LIMIT_EXCEEDED,
				"The upload request exceeds the configured transport limit.",
				List.of(),
				Map.of(),
				request);
	}

	@ExceptionHandler({
		MissingServletRequestPartException.class,
		MultipartException.class
	})
	ResponseEntity<ErrorResponse> handleMalformedMultipart(HttpServletRequest request) {
		return response(
				HttpStatus.BAD_REQUEST,
				ErrorCode.MALFORMED_REQUEST,
				"The multipart request is malformed.",
				List.of(),
				Map.of(),
				request);
	}

	@ExceptionHandler({
		MissingServletRequestParameterException.class,
		MethodArgumentTypeMismatchException.class
	})
	ResponseEntity<ErrorResponse> handleInvalidRequestParameter(HttpServletRequest request) {
		boolean multipart = request.getContentType() != null
				&& request.getContentType().startsWith("multipart/");
		return response(
				HttpStatus.BAD_REQUEST,
				multipart ? ErrorCode.MALFORMED_REQUEST : ErrorCode.VALIDATION_FAILED,
				multipart
						? "The multipart request is malformed."
						: "One or more request parameters are invalid.",
				List.of(),
				Map.of(),
				request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
		return response(
				HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				ErrorCode.MALFORMED_REQUEST,
				"The request content type is not supported.",
				List.of(),
				Map.of(),
				request);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ErrorResponse> handleAuthentication(HttpServletRequest request) {
		return response(
				HttpStatus.UNAUTHORIZED,
				ErrorCode.AUTHENTICATION_REQUIRED,
				"Authentication is required.",
				List.of(),
				Map.of(),
				request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ErrorResponse> handleAccessDenied(HttpServletRequest request) {
		return response(
				HttpStatus.FORBIDDEN,
				ErrorCode.ACCESS_DENIED,
				"You do not have permission to perform this action.",
				List.of(),
				Map.of(),
				request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				ErrorCode.RESOURCE_NOT_FOUND,
				"The requested resource was not found.",
				List.of(),
				Map.of(),
				request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(
			Exception exception, HttpServletRequest request) {
		log.error("Unhandled request failure", exception);
		return response(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCode.INTERNAL_ERROR,
				"An unexpected error occurred.",
				List.of(),
				Map.of(),
				request);
	}

	private ResponseEntity<ErrorResponse> response(
			org.springframework.http.HttpStatusCode status,
			ErrorCode code,
			String message,
			List<ErrorResponse.FieldError> fieldErrors,
			Map<String, Object> metadata,
			HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
		org.slf4j.MDC.put("errorCode", code.name());
		ErrorResponse body = new ErrorResponse(
				code.name(), message, requestId == null ? "unknown" : requestId.toString(), fieldErrors, metadata);
		return ResponseEntity.status(status).body(body);
	}
}
