package com.sealease.backend.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single error response shape returned by every API endpoint, including security failures.
 *
 * @param fieldErrors populated only for request validation failures; omitted otherwise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path,
		String correlationId,
		List<FieldError> fieldErrors) {

	public ApiError {
		fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
	}

	public record FieldError(String field, String message) {
	}

}
