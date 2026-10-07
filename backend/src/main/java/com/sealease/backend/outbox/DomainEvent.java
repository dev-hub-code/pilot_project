package com.sealease.backend.outbox;

import java.util.Map;
import java.util.Objects;

/**
 * An integration event bound for Kafka. {@code payload} must be JSON-serialisable and, like audit
 * values, never carry secrets or full bank/identity numbers.
 *
 * @param aggregateId also the Kafka record key, so events of one aggregate stay ordered
 */
public record DomainEvent(String topic, String eventType, String aggregateType, String aggregateId,
		Map<String, ?> payload) {

	public DomainEvent {
		Objects.requireNonNull(topic, "topic");
		Objects.requireNonNull(eventType, "eventType");
		Objects.requireNonNull(aggregateType, "aggregateType");
		Objects.requireNonNull(aggregateId, "aggregateId");
		payload = Map.copyOf(payload);
	}

	public static DomainEvent of(String topic, String eventType, String aggregateType, Object aggregateId,
			Map<String, ?> payload) {
		return new DomainEvent(topic, eventType, aggregateType, aggregateId.toString(), payload);
	}

}
