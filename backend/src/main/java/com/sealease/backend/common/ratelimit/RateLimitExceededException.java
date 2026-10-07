package com.sealease.backend.common.ratelimit;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;

import java.time.Duration;

public class RateLimitExceededException extends BusinessException {

	private final Duration retryAfter;

	public RateLimitExceededException(Duration retryAfter) {
		super(ErrorCode.TOO_MANY_REQUESTS, "Too many requests; try again later");
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() {
		return retryAfter;
	}

}
