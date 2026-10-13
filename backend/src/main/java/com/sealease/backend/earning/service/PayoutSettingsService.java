package com.sealease.backend.earning.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.earning.dto.PayoutSettingsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The payout day of the month, set by an admin (one row, {@code payout_settings}). */
@Service
public class PayoutSettingsService {

	private static final String ENTITY = "PAYOUT_SETTINGS";

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public PayoutSettingsService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PayoutSettingsResponse get() {
		return jdbc.queryForObject("SELECT payout_day, updated_at FROM payout_settings WHERE id = 1",
				(rs, i) -> new PayoutSettingsResponse(rs.getInt("payout_day"), rs.getTimestamp("updated_at").toInstant()));
	}

	/** The day new payout schedules use. */
	@Transactional(readOnly = true)
	public int payoutDay() {
		return get().payoutDay();
	}

	@Transactional
	public PayoutSettingsResponse update(UUID actorId, int payoutDay) {
		int before = jdbc.queryForObject("SELECT payout_day FROM payout_settings WHERE id = 1 FOR UPDATE", Integer.class);
		if (before != payoutDay) {
			Instant now = clock.instant();
			jdbc.update("UPDATE payout_settings SET payout_day = ?, updated_at = ?, updated_by = ? WHERE id = 1", payoutDay,
					Timestamp.from(now), actorId);
			audit.record(AuditRecord.of(actorId, AuditAction.PAYOUT_DAY_CHANGED, ENTITY, null)
				.withOldValue(Map.of("payoutDay", before))
				.withNewValue(Map.of("payoutDay", payoutDay)));
		}
		return get();
	}

}
