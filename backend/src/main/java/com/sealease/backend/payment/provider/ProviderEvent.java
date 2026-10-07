package com.sealease.backend.payment.provider;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * A verified provider notification.
 *
 * @param eventId the provider's id for this notification, used to ignore redeliveries
 * @param payload the notification as received, kept for reconciliation
 */
public record ProviderEvent(String eventId, Outcome outcome, String providerReference, BigDecimal amount,
		String currency, String failureReason, Map<String, ?> payload) {

	public enum Outcome {
		SUCCEEDED,
		FAILED
	}

	public ProviderEvent {
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(providerReference, "providerReference");
		payload = Map.copyOf(payload);
	}

}
