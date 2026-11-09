package com.sealease.backend.audit;

import com.sealease.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sign-in events are deleted after 30 days; the rest of the audit log, and every manual change, stay refused. */
@IntegrationTest
class AuditRetentionIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private AuditRetention retention;

	@Test
	void signInEventsOlderThan30DaysAreDeletedAndEverythingElseIsKept() {
		String marker = UUID.randomUUID().toString();
		Instant old = Instant.now().minus(Duration.ofDays(31));
		Instant recent = Instant.now().minus(Duration.ofDays(29));
		for (AuditAction action : AuditRetention.PURGEABLE) {
			insert(action, old, marker);
		}
		UUID recentLogin = insert(AuditAction.LOGIN, recent, marker);
		UUID oldPayment = insert(AuditAction.PAYMENT_SUCCEEDED, old, marker);
		UUID oldRegistration = insert(AuditAction.USER_REGISTERED, old, marker);
		UUID oldRoleChange = insert(AuditAction.USER_ROLES_CHANGED, old, marker);

		int deleted = retention.purgeExpired();

		assertThat(deleted).isGreaterThanOrEqualTo(AuditRetention.PURGEABLE.size());
		assertThat(jdbc.queryForList("SELECT id FROM audit_logs WHERE entity_id = ?", UUID.class, marker))
			.as("only the recent sign-in and the other kinds of record are left")
			.containsExactlyInAnyOrder(recentLogin, oldPayment, oldRegistration, oldRoleChange);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM audit_logs WHERE action = 'AUDIT_LOG_PURGED' AND (new_value ->> 'deleted')::int >= ?""",
				Integer.class, AuditRetention.PURGEABLE.size())).as("the deletion is itself recorded").isPositive();

		// Nothing left to delete: a second run deletes nothing.
		insert(AuditAction.LOGIN, recent, marker);
		assertThat(retention.purgeExpired()).isZero();
	}

	@Test
	void theAuditLogStillRefusesEveryOtherDeleteAndEveryUpdate() {
		String marker = UUID.randomUUID().toString();
		Instant old = Instant.now().minus(Duration.ofDays(60));
		UUID login = insert(AuditAction.LOGIN, old, marker);
		UUID payment = insert(AuditAction.PAYMENT_SUCCEEDED, old, marker);

		// Without the retention switch, not even an old sign-in may be deleted.
		assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id = ?", login))
			.isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
		// With it, still only sign-in events, and never an update.
		assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
			jdbc.queryForObject("SELECT set_config('sealease.audit_retention', 'on', true)", String.class);
			jdbc.update("DELETE FROM audit_logs WHERE id = ?", payment);
		})).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
		assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
			jdbc.queryForObject("SELECT set_config('sealease.audit_retention', 'on', true)", String.class);
			jdbc.update("UPDATE audit_logs SET entity_type = 'x' WHERE id = ?", login);
		})).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?", Integer.class, marker))
			.isEqualTo(2);
	}

	private UUID insert(AuditAction action, Instant at, String marker) {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO audit_logs (id, action, entity_type, entity_id, created_at) VALUES (?, ?, 'test', ?, ?)",
				id, action.name(), marker, OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
		return id;
	}

}
