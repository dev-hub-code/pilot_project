package com.sealease.backend.common.web;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * The {@code Idempotency-Key} request header required on money-moving POSTs (checkout, payments).
 * A client generates one key per user intent and reuses it on every retry of that intent.
 */
public final class IdempotencyKey {

	public static final String HEADER = "Idempotency-Key";

	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{8,100}");

	private IdempotencyKey() {
	}

	public static String require(String candidate) {
		if (candidate == null || !VALID.matcher(candidate).matches()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST,
					HEADER + " must be 8-100 letters, digits, '-' or '_'");
		}
		return candidate;
	}

}
