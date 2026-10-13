package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.withdrawal.dto.ReferralPayoutMode;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * How each investor's referral commission is withdrawn: by bank (in the monthly referral file) or in cash at the
 * office. The investor chooses once per calendar year; until they do, it is by bank when they have a verified
 * primary bank account, otherwise in cash. Staff may set it on the investor's behalf (on request), which counts as
 * the year's choice.
 */
@Service
public class ReferralPayoutModeService {

	private static final String ENTITY = "USER";

	private final JdbcTemplate jdbc;
	private final BankAccountService bankAccounts;
	private final AuditService audit;
	private final Clock clock;

	public ReferralPayoutModeService(JdbcTemplate jdbc, BankAccountService bankAccounts, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.bankAccounts = bankAccounts;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ReferralPayoutMode current(UUID userId) {
		int year = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).getYear();
		return jdbc.query("SELECT mode, chosen_at FROM referral_payout_modes WHERE user_id = ? AND year = ?",
				(rs, i) -> new ReferralPayoutMode(year, WithdrawalPayoutMethod.valueOf(rs.getString("mode")), true,
						rs.getTimestamp("chosen_at").toInstant()), userId, (short) year)
			.stream().findFirst()
			.orElseGet(() -> new ReferralPayoutMode(year, bankAccounts.verifiedPrimaryAccount(userId).isPresent()
					? WithdrawalPayoutMethod.BANK_TRANSFER : WithdrawalPayoutMethod.OFFICE_CASH, false, null));
	}

	/** The investor's choice for this year; refused once they have chosen. */
	@Transactional
	public ReferralPayoutMode choose(UUID userId, WithdrawalPayoutMethod mode) {
		if (mode == WithdrawalPayoutMethod.BANK_TRANSFER && bankAccounts.verifiedPrimaryAccount(userId).isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Add a bank account and have it verified before choosing to be paid by bank");
		}
		ReferralPayoutMode current = current(userId);
		if (current.chosen()) {
			throw new BusinessException(ErrorCode.CONFLICT, "You have already chosen how referral earnings are paid in "
					+ current.year() + ". Contact support if it needs changing.");
		}
		return save(userId, userId, mode, current, false);
	}

	/** Staff set (or change) the investor's mode for this year, at their request. */
	@Transactional
	public ReferralPayoutMode set(UUID actorId, UUID userId, WithdrawalPayoutMethod mode) {
		return save(actorId, userId, mode, current(userId), true);
	}

	private ReferralPayoutMode save(UUID actorId, UUID userId, WithdrawalPayoutMethod mode, ReferralPayoutMode before,
			boolean replace) {
		Instant now = clock.instant();
		jdbc.update("""
				INSERT INTO referral_payout_modes (id, user_id, year, mode, chosen_by, chosen_at) VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (user_id, year) DO UPDATE SET mode = EXCLUDED.mode, chosen_by = EXCLUDED.chosen_by,
				    chosen_at = EXCLUDED.chosen_at
				WHERE ?
				""", UUID.randomUUID(), userId, (short) before.year(), mode.name(), actorId, Timestamp.from(now), replace);
		audit.record(AuditRecord.of(actorId, AuditAction.REFERRAL_PAYOUT_MODE_CHOSEN, ENTITY, userId)
			.withOldValue(before.chosen() ? Map.of("mode", before.mode()) : null)
			.withNewValue(Map.of("year", before.year(), "mode", mode)));
		return current(userId);
	}

}
