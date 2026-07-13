package com.knowledgehub.api.common;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class ApiException extends RuntimeException {

	private final ErrorCode code;
	private final HttpStatusCode status;
	private final Map<String, Object> metadata;

	public ApiException(ErrorCode code, HttpStatusCode status, String message) {
		this(code, status, message, Map.of());
	}

	public ApiException(
			ErrorCode code, HttpStatusCode status, String message, Map<String, Object> metadata) {
		super(message);
		this.code = code;
		this.status = status;
		this.metadata = Map.copyOf(metadata);
	}
}
