package com.sealease.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Platform-wide error codes. The {@code code} string in {@link com.sealease.backend.common.api.ApiError}
 * is the enum name, which clients may rely on; HTTP status is derived from it.
 */
public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
	ACCOUNT_LOCKED(HttpStatus.LOCKED),
	ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
	CONFLICT(HttpStatus.CONFLICT),
	CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
	EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
	BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

}
