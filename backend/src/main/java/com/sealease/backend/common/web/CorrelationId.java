package com.sealease.backend.common.web;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Header names, MDC keys and accessors for request tracing identifiers.
 *
 * <ul>
 * <li><b>Correlation ID</b> - spans a whole business flow; accepted from the caller (e.g. the
 * Next.js server) when well formed, and propagated into Kafka events.</li>
 * <li><b>Request ID</b> - always generated server-side; unique per HTTP request.</li>
 * </ul>
 */
public final class CorrelationId {

	public static final String CORRELATION_HEADER = "X-Correlation-Id";
	public static final String REQUEST_HEADER = "X-Request-Id";
	public static final String CORRELATION_MDC_KEY = "correlationId";
	public static final String REQUEST_MDC_KEY = "requestId";

	/** Caller-supplied IDs are copied into logs and headers, so restrict them to a safe alphabet. */
	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

	private CorrelationId() {
	}

	public static boolean isValid(String candidate) {
		return candidate != null && VALID.matcher(candidate).matches();
	}

	public static Optional<String> current() {
		return Optional.ofNullable(MDC.get(CORRELATION_MDC_KEY));
	}

}
