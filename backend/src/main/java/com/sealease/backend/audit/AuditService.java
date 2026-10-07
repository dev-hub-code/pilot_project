package com.sealease.backend.audit;

import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.common.web.CorrelationId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Writes audit entries to the append-only {@code audit_logs} table.
 *
 * <p>Entries join the caller's transaction ({@link Propagation#MANDATORY}): an audited change and
 * its audit entry commit or roll back together, so the log can never claim something happened
 * that did not, nor miss something that did. Request metadata (IP, user agent, correlation ID) is
 * captured automatically from the current request when there is one.
 */
@Service
public class AuditService {

	private static final String INSERT = """
			INSERT INTO audit_logs (id, actor_user_id, action, entity_type, entity_id, old_value, new_value,
			                        ip_address, user_agent, correlation_id, created_at)
			VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbc;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public AuditService(JdbcTemplate jdbc, JsonMapper jsonMapper, Clock clock) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(AuditRecord entry) {
		ClientInfo client = ClientInfo.current();
		jdbc.update(INSERT,
				UUID.randomUUID(),
				entry.actorUserId(),
				entry.action().name(),
				entry.entityType(),
				entry.entityId(),
				toJson(entry.oldValue()),
				toJson(entry.newValue()),
				client.ipAddress(),
				client.userAgent(),
				CorrelationId.current().orElse(null),
				OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
	}

	private String toJson(Map<String, ?> value) {
		return value == null || value.isEmpty() ? null : jsonMapper.writeValueAsString(value);
	}

}
