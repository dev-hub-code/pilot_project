package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.withdrawal.dto.WithdrawalSettingsResponse;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/** The days of the month withdrawals are made, set by an admin (one row, {@code withdrawal_settings}). */
@Service
public class WithdrawalSettingsService {

	private static final String ENTITY = "WITHDRAWAL_SETTINGS";

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public WithdrawalSettingsService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public WithdrawalSettingsResponse get() {
		return jdbc.queryForObject("SELECT rent_withdrawal_day, referral_withdrawal_day, updated_at FROM withdrawal_settings WHERE id = 1",
				(rs, i) -> {
					int rent = rs.getInt("rent_withdrawal_day");
					int referral = rs.getInt("referral_withdrawal_day");
					return new WithdrawalSettingsResponse(rent, referral, nextFileOn(WithdrawalKind.RENT, rent),
							nextFileOn(WithdrawalKind.REFERRAL, referral), rs.getTimestamp("updated_at").toInstant());
				});
	}

	/** The withdrawal day of a kind of withdrawal: bank payment, and cash at the office from then on. */
	@Transactional(readOnly = true)
	public int withdrawalDay(WithdrawalKind kind) {
		WithdrawalSettingsResponse settings = get();
		return kind == WithdrawalKind.RENT ? settings.rentWithdrawalDay() : settings.referralWithdrawalDay();
	}

	/** Takes effect from the next run: a month that has already run is not run again. */
	@Transactional
	public WithdrawalSettingsResponse update(UUID actorId, int rentDay, int referralDay) {
		Map<String, Object> before = jdbc.queryForMap(
				"SELECT rent_withdrawal_day, referral_withdrawal_day FROM withdrawal_settings WHERE id = 1 FOR UPDATE");
		int rentBefore = ((Number) before.get("rent_withdrawal_day")).intValue();
		int referralBefore = ((Number) before.get("referral_withdrawal_day")).intValue();
		if (rentBefore != rentDay || referralBefore != referralDay) {
			jdbc.update("""
					UPDATE withdrawal_settings SET rent_withdrawal_day = ?, referral_withdrawal_day = ?, updated_at = ?,
					    updated_by = ? WHERE id = 1""", rentDay, referralDay, Timestamp.from(clock.instant()), actorId);
			audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_DAY_CHANGED, ENTITY, null)
				.withOldValue(Map.of("rentWithdrawalDay", rentBefore, "referralWithdrawalDay", referralBefore))
				.withNewValue(Map.of("rentWithdrawalDay", rentDay, "referralWithdrawalDay", referralDay)));
		}
		return get();
	}

	/** The day a month's bank file is made: the day before its withdrawal day. */
	static LocalDate fileDate(YearMonth month, int withdrawalDay) {
		return month.atDay(withdrawalDay - 1);
	}

	/** This month's file date, or today when that has passed without a run (the job picks it up shortly). */
	private LocalDate nextFileOn(WithdrawalKind kind, int withdrawalDay) {
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		YearMonth month = YearMonth.from(today);
		boolean ran = Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT exists(SELECT 1 FROM scheduled_withdrawal_runs WHERE period = ? AND kind = ?)", Boolean.class,
				month.toString(), kind.name()));
		if (ran) {
			return fileDate(month.plusMonths(1), withdrawalDay);
		}
		LocalDate date = fileDate(month, withdrawalDay);
		return date.isBefore(today) ? today : date;
	}

}
