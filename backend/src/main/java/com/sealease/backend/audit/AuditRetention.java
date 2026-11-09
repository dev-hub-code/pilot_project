package com.sealease.backend.audit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes sign-in events (logins, logouts, failed logins, lockouts, password resets and changes, email verification)
 * once they are older than the retention period. Everything else in the audit log is kept for good.
 *
 * <p>The database refuses any other delete: the audit log's trigger lets through only these actions, and only in a
 * transaction that has switched on {@code sealease.audit_retention}, as each batch here does.
 */
@Service
public class AuditRetention {

	/** Must match the list in the audit_logs_guard() trigger (V32). */
	public static final Set<AuditAction> PURGEABLE = Set.of(AuditAction.LOGIN, AuditAction.LOGIN_FAILED,
			AuditAction.LOGOUT, AuditAction.LOGOUT_ALL, AuditAction.ACCOUNT_LOCKED,
			AuditAction.TOKEN_REFRESH_REUSE_DETECTED, AuditAction.PASSWORD_RESET_REQUESTED, AuditAction.PASSWORD_RESET,
			AuditAction.PASSWORD_CHANGE, AuditAction.EMAIL_VERIFIED, AuditAction.EMAIL_VERIFICATION_SENT);

	private static final String ACTIONS = PURGEABLE.stream().map(a -> "'" + a.name() + "'").sorted()
		.collect(Collectors.joining(", "));

	private static final String DELETE_BATCH = """
			DELETE FROM audit_logs WHERE id IN (
			    SELECT id FROM audit_logs WHERE action IN (%s) AND created_at < ? LIMIT ?)""".formatted(ACTIONS);

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final AuditService audit;
	private final Clock clock;
	private final Duration keepFor;
	private final int batchSize;

	public AuditRetention(JdbcTemplate jdbc, TransactionTemplate transactions, AuditService audit, Clock clock,
			@Value("${app.audit.sign-in-retention:30d}") Duration keepFor,
			@Value("${app.audit.purge-batch-size:5000}") int batchSize) {
		if (keepFor.isNegative() || keepFor.isZero()) {
			throw new IllegalArgumentException("app.audit.sign-in-retention must be positive");
		}
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.audit = audit;
		this.clock = clock;
		this.keepFor = keepFor;
		this.batchSize = batchSize;
	}

	/** Deletes the sign-in events older than the retention period, in batches; returns how many were deleted. */
	public int purgeExpired() {
		Instant cutoff = clock.instant().minus(keepFor);
		OffsetDateTime before = OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC);
		int total = 0;
		int deleted;
		do {
			deleted = transactions.execute(status -> {
				// Only for this transaction: the trigger then allows deleting the actions above.
				jdbc.queryForObject("SELECT set_config('sealease.audit_retention', 'on', true)", String.class);
				return jdbc.update(DELETE_BATCH, before, batchSize);
			});
			total += deleted;
		}
		while (deleted == batchSize);

		if (total > 0) {
			int count = total;
			transactions.executeWithoutResult(status -> audit.record(
					AuditRecord.of(null, AuditAction.AUDIT_LOG_PURGED, "audit_log", null)
						.withNewValue(Map.of("deleted", count, "olderThan", cutoff.toString(),
								"retentionDays", keepFor.toDays()))));
		}
		return total;
	}

}
