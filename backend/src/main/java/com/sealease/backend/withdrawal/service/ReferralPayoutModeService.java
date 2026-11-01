package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.referral.dto.ReferralScheme;
import com.sealease.backend.referral.service.ReferralSchemeService;
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
 * How each investor's returns and referral commission are paid out. Online investors are always paid by bank and cannot
 * change it. Network investors choose bank or cash at the office and may change it at any time; until they choose it is
 * by bank when they have a verified primary bank account, otherwise in cash. Staff may set it for a network investor on
 * their behalf. A choice applies to withdrawals made from then on.
 */
@Service
public class ReferralPayoutModeService {

	private static final String ENTITY = "USER";

	private final JdbcTemplate jdbc;
	private final BankAccountService bankAccounts;
	private final ReferralSchemeService schemes;
	private final AuditService audit;
	private final Clock clock;

	public ReferralPayoutModeService(JdbcTemplate jdbc, BankAccountService bankAccounts, ReferralSchemeService schemes,
			AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.bankAccounts = bankAccounts;
		this.schemes = schemes;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ReferralPayoutMode current(UUID userId) {
		int year = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).getYear();
		if (schemes.schemeOf(userId) == ReferralScheme.ONLINE) {
			return new ReferralPayoutMode(year, WithdrawalPayoutMethod.BANK_TRANSFER, false, null, false);
		}
		return jdbc.query("SELECT mode, chosen_at FROM referral_payout_modes WHERE user_id = ? ORDER BY chosen_at DESC LIMIT 1",
				(rs, i) -> new ReferralPayoutMode(year, WithdrawalPayoutMethod.valueOf(rs.getString("mode")), true,
						rs.getTimestamp("chosen_at").toInstant(), true), userId)
			.stream().findFirst()
			.orElseGet(() -> new ReferralPayoutMode(year, bankAccounts.verifiedPrimaryAccount(userId).isPresent()
					? WithdrawalPayoutMethod.BANK_TRANSFER : WithdrawalPayoutMethod.OFFICE_CASH, false, null, true));
	}

	/** A network investor's choice, at any time; refused for online investors, who are always paid by bank. */
	@Transactional
	public ReferralPayoutMode choose(UUID userId, WithdrawalPayoutMethod mode) {
		return save(userId, userId, mode);
	}

	/** Staff set a network investor's choice, at their request. */
	@Transactional
	public ReferralPayoutMode set(UUID actorId, UUID userId, WithdrawalPayoutMethod mode) {
		return save(actorId, userId, mode);
	}

	private ReferralPayoutMode save(UUID actorId, UUID userId, WithdrawalPayoutMethod mode) {
		ReferralPayoutMode before = current(userId);
		if (!before.changeable()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Online investors are always paid by bank");
		}
		if (mode == WithdrawalPayoutMethod.BANK_TRANSFER && bankAccounts.verifiedPrimaryAccount(userId).isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Add a bank account and have it verified before choosing to be paid by bank");
		}
		Instant now = clock.instant();
		jdbc.update("""
				INSERT INTO referral_payout_modes (id, user_id, year, mode, chosen_by, chosen_at) VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (user_id, year) DO UPDATE SET mode = EXCLUDED.mode, chosen_by = EXCLUDED.chosen_by,
				    chosen_at = EXCLUDED.chosen_at
				""", UUID.randomUUID(), userId, (short) before.year(), mode.name(), actorId, Timestamp.from(now));
		audit.record(AuditRecord.of(actorId, AuditAction.REFERRAL_PAYOUT_MODE_CHOSEN, ENTITY, userId)
			.withOldValue(before.chosen() ? Map.of("mode", before.mode()) : null)
			.withNewValue(Map.of("year", before.year(), "mode", mode)));
		return current(userId);
	}

}
