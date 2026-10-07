package com.sealease.backend.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Consumer-side de-duplication. A Kafka listener calls {@link #markProcessed} first, inside the
 * transaction that applies the event's effects, and skips the event when it returns {@code false}:
 * a redelivered event then changes nothing.
 */
@Service
public class ProcessedEvents {

	private final JdbcTemplate jdbc;
	private final Clock clock;

	public ProcessedEvents(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** @return {@code true} the first time this consumer sees the event, {@code false} on redelivery */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean markProcessed(String consumer, UUID eventId) {
		return jdbc.update("""
				INSERT INTO processed_events (consumer, event_id, processed_at) VALUES (?, ?, ?)
				ON CONFLICT DO NOTHING
				""", consumer, eventId, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)) == 1;
	}

}
