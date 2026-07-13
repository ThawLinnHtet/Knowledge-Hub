package com.knowledgehub.api.common;

import java.util.List;
import java.util.Map;

public record ErrorResponse(
		String code,
		String message,
		String requestId,
		List<FieldError> fieldErrors,
		Map<String, Object> metadata) {

	public record FieldError(String field, String code, String message) {}
}
