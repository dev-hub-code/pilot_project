package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.withdrawal.dto.WithdrawalSettingsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * The minimum withdrawal, set by an admin (one row, {@code withdrawal_settings}). Until it is set, the configured
 * {@code app.withdrawals.minimum-amount} applies.
 */
@Service
public class WithdrawalSettingsService {

	private static final String ENTITY = "WITHDRAWAL_SETTINGS";

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final WithdrawalProperties properties;
	private final MoneyProperties money;
	private final Clock clock;

	public WithdrawalSettingsService(JdbcTemplate jdbc, AuditService audit, WithdrawalProperties properties,
			MoneyProperties money, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.properties = properties;
		this.money = money;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public WithdrawalSettingsResponse get() {
		return jdbc.queryForObject("SELECT minimum_amount, updated_at FROM withdrawal_settings WHERE id = 1", (rs, i) -> {
			BigDecimal set = rs.getBigDecimal("minimum_amount");
			Timestamp updatedAt = rs.getTimestamp("updated_at");
			return new WithdrawalSettingsResponse(set == null ? properties.minimumAmount() : set.stripTrailingZeros(),
					money.defaultCurrency().getCurrencyCode(), properties.minimumAmount(),
					set == null || updatedAt == null ? null : updatedAt.toInstant());
		});
	}

	/** The amount a run's row must come to, in the platform's default currency. */
	@Transactional(readOnly = true)
	public BigDecimal minimumAmount() {
		return get().minimumAmount();
	}

	@Transactional
	public WithdrawalSettingsResponse update(UUID actorId, BigDecimal minimumAmount) {
		BigDecimal before = jdbc.queryForObject("SELECT minimum_amount FROM withdrawal_settings WHERE id = 1 FOR UPDATE",
				BigDecimal.class);
		BigDecimal effectiveBefore = before == null ? properties.minimumAmount() : before;
		if (before == null || effectiveBefore.compareTo(minimumAmount) != 0) {
			jdbc.update("UPDATE withdrawal_settings SET minimum_amount = ?, updated_at = ?, updated_by = ? WHERE id = 1",
					minimumAmount, Timestamp.from(clock.instant()), actorId);
			audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_MINIMUM_CHANGED, ENTITY, null)
				.withOldValue(Map.of("minimumAmount", effectiveBefore.toPlainString()))
				.withNewValue(Map.of("minimumAmount", minimumAmount.toPlainString())));
		}
		return get();
	}

}
