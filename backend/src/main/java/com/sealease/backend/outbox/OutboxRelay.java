package com.sealease.backend.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Moves committed outbox events to Kafka, oldest first.
 *
 * <p>Rows are claimed with {@code FOR UPDATE SKIP LOCKED}, so concurrent relays (several app
 * instances) never publish the same row twice in parallel. A row is marked published only after
 * the broker acknowledged it; if the process dies in between, the event is sent again - delivery
 * is at-least-once and consumers de-duplicate on the {@code eventId} header (see
 * {@link ProcessedEvents}). On the first failure the batch stops, so later events of the same
 * aggregate never overtake an earlier one.
 */
@Component
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private static final String CLAIM = """
			SELECT id, topic, event_type, aggregate_type, aggregate_id, payload::text AS payload, correlation_id, created_at
			FROM outbox_events
			WHERE published_at IS NULL
			ORDER BY created_at, id
			LIMIT ?
			FOR UPDATE SKIP LOCKED
			""";

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final KafkaOperations<String, String> kafka;
	private final JsonMapper jsonMapper;
	private final OutboxProperties properties;
	private final Clock clock;

	public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions, KafkaOperations<String, String> kafka,
			JsonMapper jsonMapper, OutboxProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.kafka = kafka;
		this.jsonMapper = jsonMapper;
		this.properties = properties;
		this.clock = clock;
	}

	/** @return the number of events published */
	public int publishPending() {
		Integer published = transactions.execute(status -> {
			List<Row> rows = jdbc.query(CLAIM, (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
					rs.getString("event_type"), rs.getString("aggregate_type"), rs.getString("aggregate_id"),
					rs.getString("payload"), rs.getString("correlation_id"), rs.getTimestamp("created_at")),
					properties.batchSize());
			int sent = 0;
			for (Row row : rows) {
				try {
					send(row);
				}
				catch (Exception ex) {
					log.warn("Outbox event {} ({}) could not be published; will retry", row.id(), row.eventType(), ex);
					jdbc.update("UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE id = ?",
							truncate(ex.toString()), row.id());
					break;
				}
				jdbc.update("UPDATE outbox_events SET published_at = ?, attempts = attempts + 1 WHERE id = ?",
						OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), row.id());
				sent++;
			}
			return sent;
		});
		return published == null ? 0 : published;
	}

	private void send(Row row) throws Exception {
		ObjectNode envelope = jsonMapper.createObjectNode();
		envelope.put("eventId", row.id().toString());
		envelope.put("eventType", row.eventType());
		envelope.put("aggregateType", row.aggregateType());
		envelope.put("aggregateId", row.aggregateId());
		envelope.put("occurredAt", row.createdAt().toInstant().toString());
		envelope.set("data", jsonMapper.readTree(row.payload()));

		ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.aggregateId(),
				jsonMapper.writeValueAsString(envelope));
		record.headers().add("eventId", bytes(row.id().toString()));
		record.headers().add("eventType", bytes(row.eventType()));
		if (row.correlationId() != null) {
			record.headers().add("X-Correlation-Id", bytes(row.correlationId()));
		}
		kafka.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static String truncate(String value) {
		return value.length() <= 1000 ? value : value.substring(0, 1000);
	}

	private record Row(UUID id, String topic, String eventType, String aggregateType, String aggregateId,
			String payload, String correlationId, Timestamp createdAt) {
	}

}
