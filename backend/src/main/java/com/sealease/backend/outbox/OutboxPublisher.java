package com.sealease.backend.outbox;

import com.sealease.backend.common.web.CorrelationId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Transactional outbox: events are stored in the caller's transaction ({@link Propagation#MANDATORY})
 * and published to Kafka afterwards by {@link OutboxRelay}. An event therefore exists if and only
 * if the business change that raised it committed - no lost or phantom events.
 */
@Service
public class OutboxPublisher {

	private static final String INSERT = """
			INSERT INTO outbox_events (id, topic, event_type, aggregate_type, aggregate_id, payload, correlation_id,
			                           created_at)
			VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
			""";

	private final JdbcTemplate jdbc;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public OutboxPublisher(JdbcTemplate jdbc, JsonMapper jsonMapper, Clock clock) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	/** @return the event id, which consumers use for de-duplication */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID publish(DomainEvent event) {
		UUID id = UUID.randomUUID();
		jdbc.update(INSERT, id, event.topic(), event.eventType(), event.aggregateType(), event.aggregateId(),
				jsonMapper.writeValueAsString(event.payload()), CorrelationId.current().orElse(null),
				OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
		return id;
	}

}
