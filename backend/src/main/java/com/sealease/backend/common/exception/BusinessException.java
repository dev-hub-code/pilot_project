package com.sealease.backend.common.exception;

import java.util.Objects;

/**
 * Base type for every expected, client-facing failure raised by a business module. The message is
 * returned to the client verbatim, so it must never contain internal details or sensitive data.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
	}

	public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

}
