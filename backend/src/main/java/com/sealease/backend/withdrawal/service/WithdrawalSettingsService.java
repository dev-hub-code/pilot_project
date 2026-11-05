package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.withdrawal.dto.WithdrawalSettingsRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalSettingsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Withdrawal settings, set by an admin (one row, {@code withdrawal_settings}): the minimum withdrawal, charges and tax
 * withheld, the amount above which a second approver is needed, and the processing time shown to investors. Until an
 * admin sets them, the configured {@code app.withdrawals.*} defaults apply; charges and tax start at zero.
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
		return jdbc.queryForObject("SELECT * FROM withdrawal_settings WHERE id = 1", (rs, i) -> {
			BigDecimal minimum = rs.getBigDecimal("minimum_amount");
			BigDecimal threshold = rs.getBigDecimal("dual_approval_threshold");
			int days = rs.getInt("processing_days");
			Timestamp updatedAt = rs.getTimestamp("updated_at");
			return new WithdrawalSettingsResponse(
					minimum == null ? properties.minimumAmount() : minimum.stripTrailingZeros(),
					money.defaultCurrency().getCurrencyCode(), properties.minimumAmount(),
					plain(rs.getBigDecimal("fee_flat")), plain(rs.getBigDecimal("fee_percent")),
					plain(rs.getBigDecimal("tax_percent")),
					threshold == null ? properties.dualApprovalThreshold() : threshold.stripTrailingZeros(),
					properties.dualApprovalThreshold(), days == 0 ? null : days,
					updatedAt == null ? null : updatedAt.toInstant());
		});
	}

	/** The amount a run's row (or a request) must come to, in the platform's default currency. */
	@Transactional(readOnly = true)
	public BigDecimal minimumAmount() {
		return get().minimumAmount();
	}

	/** The charges and tax a withdrawal pays now. */
	@Transactional(readOnly = true)
	public WithdrawalCalculator.Charges charges() {
		WithdrawalSettingsResponse s = get();
		return new WithdrawalCalculator.Charges(s.feeFlat(), s.feePercent(), s.taxPercent());
	}

	/** Above this amount, a withdrawal needs a second, different approver. */
	@Transactional(readOnly = true)
	public BigDecimal dualApprovalThreshold() {
		return get().dualApprovalThreshold();
	}

	/** The minimum only (kept for the earlier form). */
	@Transactional
	public WithdrawalSettingsResponse update(UUID actorId, BigDecimal minimumAmount) {
		return update(actorId, WithdrawalSettingsRequest.minimum(minimumAmount));
	}

	/** Changes the given settings; those left out keep their value. Audited with the values before and after. */
	@Transactional
	public WithdrawalSettingsResponse update(UUID actorId, WithdrawalSettingsRequest request) {
		jdbc.queryForObject("SELECT id FROM withdrawal_settings WHERE id = 1 FOR UPDATE", Integer.class);
		WithdrawalSettingsResponse before = get();
		BigDecimal feeFlat = or(request.feeFlat(), before.feeFlat());
		BigDecimal feePercent = or(request.feePercent(), before.feePercent());
		BigDecimal taxPercent = or(request.taxPercent(), before.taxPercent());
		BigDecimal threshold = or(request.dualApprovalThreshold(), before.dualApprovalThreshold());
		Integer days = request.processingDays() == null ? before.processingDays()
				: request.processingDays() == 0 ? null : request.processingDays();
		Map<String, Object> old = values(before.minimumAmount(), before.feeFlat(), before.feePercent(), before.taxPercent(),
				before.dualApprovalThreshold(), before.processingDays());
		Map<String, Object> now = values(request.minimumAmount(), feeFlat, feePercent, taxPercent, threshold, days);
		if (!old.equals(now) || before.updatedAt() == null) {
			jdbc.update("""
					UPDATE withdrawal_settings SET minimum_amount = ?, fee_flat = ?, fee_percent = ?, tax_percent = ?,
					       dual_approval_threshold = ?, processing_days = ?, updated_at = ?, updated_by = ?
					WHERE id = 1""", request.minimumAmount(), feeFlat, feePercent, taxPercent, threshold, days,
					Timestamp.from(clock.instant()), actorId);
			AuditAction action = Objects.equals(old.get("minimumAmount"), now.get("minimumAmount"))
					? AuditAction.WITHDRAWAL_SETTINGS_CHANGED : AuditAction.WITHDRAWAL_MINIMUM_CHANGED;
			audit.record(AuditRecord.of(actorId, action, ENTITY, null).withOldValue(old).withNewValue(now));
		}
		return get();
	}

	private static Map<String, Object> values(BigDecimal minimum, BigDecimal feeFlat, BigDecimal feePercent,
			BigDecimal taxPercent, BigDecimal threshold, Integer days) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("minimumAmount", text(minimum));
		values.put("feeFlat", text(feeFlat));
		values.put("feePercent", text(feePercent));
		values.put("taxPercent", text(taxPercent));
		values.put("dualApprovalThreshold", text(threshold));
		values.put("processingDays", days == null ? "" : days.toString());
		return values;
	}

	private static String text(BigDecimal value) {
		return value == null ? "" : value.stripTrailingZeros().toPlainString();
	}

	private static BigDecimal or(BigDecimal value, BigDecimal fallback) {
		return value == null ? fallback : value;
	}

	private static BigDecimal plain(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
	}

}
