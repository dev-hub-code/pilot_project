package com.sealease.backend.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the audit log: searching it, and the counts behind the security dashboard. Read-only; entries
 * are only ever written by {@link AuditService}. Hours and days are UTC, like every date in the platform.
 */
@Service
@Transactional(readOnly = true)
public class AuditLogQuery {

	private static final String COLUMNS = """
			id, actor_user_id, action, entity_type, entity_id, old_value::text AS old_value,
			new_value::text AS new_value, ip_address, user_agent, correlation_id, created_at""";
	private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
	};

	private final NamedParameterJdbcTemplate jdbc;
	private final JsonMapper jsonMapper;
	private final RowMapper<AuditEntry> entries;

	public AuditLogQuery(NamedParameterJdbcTemplate jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
		this.entries = (rs, row) -> new AuditEntry(
				rs.getObject("id", UUID.class),
				rs.getObject("actor_user_id", UUID.class),
				AuditAction.valueOf(rs.getString("action")),
				rs.getString("entity_type"),
				rs.getString("entity_id"),
				json(rs.getString("old_value")),
				json(rs.getString("new_value")),
				rs.getString("ip_address"),
				rs.getString("user_agent"),
				rs.getString("correlation_id"),
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

	/** Newest first. */
	public Page<AuditEntry> search(AuditLogFilter filter, Pageable pageable) {
		Where where = where(filter);
		Long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs" + where.sql, where.params, Long.class);
		MapSqlParameterSource params = where.params.addValue("limit", pageable.getPageSize())
			.addValue("offset", pageable.getOffset());
		List<AuditEntry> content = jdbc.query("SELECT " + COLUMNS + " FROM audit_logs" + where.sql
				+ " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset", params, entries);
		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	/** Newest first, at most {@code limit} entries. */
	public List<AuditEntry> list(AuditLogFilter filter, int limit) {
		Where where = where(filter);
		return jdbc.query("SELECT " + COLUMNS + " FROM audit_logs" + where.sql
				+ " ORDER BY created_at DESC, id DESC LIMIT :limit", where.params.addValue("limit", limit), entries);
	}

	public Optional<AuditEntry> find(UUID id) {
		return jdbc.query("SELECT " + COLUMNS + " FROM audit_logs WHERE id = :id", Map.of("id", id), entries)
			.stream().findFirst();
	}

	/** How often each of the actions happened in [from, to); actions that did not happen count 0. */
	public Map<AuditAction, Long> countByAction(Collection<AuditAction> actions, Instant from, Instant to) {
		Map<AuditAction, Long> counts = new EnumMap<>(AuditAction.class);
		actions.forEach(a -> counts.put(a, 0L));
		jdbc.query("""
				SELECT action, count(*) AS n FROM audit_logs
				WHERE action IN (:actions) AND created_at >= :from AND created_at < :to
				GROUP BY action""", range(actions, from, to),
				rs -> {
					counts.put(AuditAction.valueOf(rs.getString("action")), rs.getLong("n"));
				});
		return counts;
	}

	/**
	 * Per UTC hour or day (whole periods, by their start) and action in [from, to); periods without any
	 * of the actions are left out.
	 */
	public List<PeriodCount> countByPeriod(Collection<AuditAction> actions, Instant from, Instant to, Bucket bucket) {
		return jdbc.query("SELECT date_trunc('" + bucket.sqlUnit + "', created_at AT TIME ZONE 'UTC') AS period, action, "
				+ "count(*) AS n FROM audit_logs WHERE action IN (:actions) AND created_at >= :from AND created_at < :to "
				+ "GROUP BY 1, 2 ORDER BY 1", range(actions, from, to),
				(rs, row) -> new PeriodCount(rs.getObject("period", LocalDateTime.class),
						AuditAction.valueOf(rs.getString("action")), rs.getLong("n")));
	}

	/** The latest entries for any of the actions in [from, to), newest first. */
	public List<AuditEntry> recent(Collection<AuditAction> actions, Instant from, Instant to, int limit) {
		return jdbc.query("SELECT " + COLUMNS + " FROM audit_logs WHERE action IN (:actions) AND created_at >= :from "
				+ "AND created_at < :to ORDER BY created_at DESC, id DESC LIMIT :limit",
				range(actions, from, to).addValue("limit", limit), entries);
	}

	/** The entities the action most often happened to in [from, to), most first. */
	public List<KeyCount> topEntities(AuditAction action, Instant from, Instant to, int limit) {
		return jdbc.query("""
				SELECT entity_id AS key, count(*) AS n, max(created_at) AS latest FROM audit_logs
				WHERE action = :action AND entity_id IS NOT NULL AND created_at >= :from AND created_at < :to
				GROUP BY entity_id ORDER BY n DESC, latest DESC LIMIT :limit""",
				range(Set.of(action), from, to).addValue("action", action.name()).addValue("limit", limit), KEY_COUNT);
	}

	/** The IP addresses the actions most often came from in [from, to), most first. */
	public List<KeyCount> topIpAddresses(Collection<AuditAction> actions, Instant from, Instant to, int limit) {
		return jdbc.query("""
				SELECT ip_address AS key, count(*) AS n, max(created_at) AS latest FROM audit_logs
				WHERE action IN (:actions) AND ip_address IS NOT NULL AND created_at >= :from AND created_at < :to
				GROUP BY ip_address ORDER BY n DESC, latest DESC LIMIT :limit""",
				range(actions, from, to).addValue("limit", limit), KEY_COUNT);
	}

	/** Per actor and action in [from, to), for actions with a known actor. */
	public List<ActorCount> countByActor(Collection<AuditAction> actions, Instant from, Instant to) {
		return jdbc.query("""
				SELECT actor_user_id, action, count(*) AS n, max(created_at) AS latest FROM audit_logs
				WHERE action IN (:actions) AND actor_user_id IS NOT NULL AND created_at >= :from AND created_at < :to
				GROUP BY actor_user_id, action""", range(actions, from, to),
				(rs, row) -> new ActorCount(rs.getObject("actor_user_id", UUID.class),
						AuditAction.valueOf(rs.getString("action")), rs.getLong("n"),
						rs.getObject("latest", OffsetDateTime.class).toInstant()));
	}

	private static final RowMapper<KeyCount> KEY_COUNT = (rs, row) -> new KeyCount(rs.getString("key"), rs.getLong("n"),
			rs.getObject("latest", OffsetDateTime.class).toInstant());

	private static MapSqlParameterSource range(Collection<AuditAction> actions, Instant from, Instant to) {
		return new MapSqlParameterSource()
			.addValue("actions", actions.stream().map(Enum::name).toList())
			.addValue("from", Timestamp.from(from))
			.addValue("to", Timestamp.from(to));
	}

	private static Where where(AuditLogFilter filter) {
		List<String> conditions = new ArrayList<>();
		MapSqlParameterSource params = new MapSqlParameterSource();
		if (filter.category() != null) {
			conditions.add("action IN (:categoryActions)");
			params.addValue("categoryActions", filter.category().actions().stream().map(Enum::name).toList());
		}
		if (filter.action() != null) {
			conditions.add("action = :action");
			params.addValue("action", filter.action().name());
		}
		if (filter.actorUserId() != null) {
			conditions.add("actor_user_id = :actor");
			params.addValue("actor", filter.actorUserId());
		}
		if (filter.entityType() != null) {
			conditions.add("entity_type = :entityType");
			params.addValue("entityType", filter.entityType());
		}
		if (filter.entityId() != null) {
			conditions.add("entity_id = :entityId");
			params.addValue("entityId", filter.entityId());
		}
		if (filter.ipAddress() != null) {
			conditions.add("ip_address = :ip");
			params.addValue("ip", filter.ipAddress());
		}
		if (filter.from() != null) {
			conditions.add("created_at >= :from");
			params.addValue("from", Timestamp.from(filter.from()));
		}
		if (filter.to() != null) {
			conditions.add("created_at < :to");
			params.addValue("to", Timestamp.from(filter.to()));
		}
		return new Where(conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions), params);
	}

	private Map<String, Object> json(String text) {
		return text == null ? null : jsonMapper.readValue(text, JSON_OBJECT);
	}

	private record Where(String sql, MapSqlParameterSource params) {
	}

	/** How {@link #countByPeriod} groups entries. */
	public enum Bucket {
		HOUR("hour"), DAY("day");

		private final String sqlUnit;

		Bucket(String sqlUnit) {
			this.sqlUnit = sqlUnit;
		}
	}

	/** @param start the UTC start of the hour or day */
	public record PeriodCount(LocalDateTime start, AuditAction action, long count) {
	}

	/** @param latest when it last happened */
	public record KeyCount(String key, long count, Instant latest) {
	}

	/** @param latest when the actor last did it */
	public record ActorCount(UUID actorUserId, AuditAction action, long count, Instant latest) {
	}

}
