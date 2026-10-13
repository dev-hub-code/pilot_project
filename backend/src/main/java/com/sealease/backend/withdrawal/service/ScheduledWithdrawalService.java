package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.dto.PayoutAccount;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import com.sealease.backend.withdrawal.dto.ScheduledRunResponse;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalBatch;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The monthly bank withdrawal runs, one for rent and one for referral commission. The day before each withdrawal
 * day (rent: the 10th, referral: the 15th by default) every eligible investor's whole balance of that kind (at
 * least the minimum withdrawal) is withdrawn to their verified primary bank account: one withdrawal each, approved
 * by the schedule, all in one batch whose Excel file is kept with it. Finance pays the file by hand on the day and
 * uploads it back ({@link PayoutBatchService#reconcile}). Referral commission of investors who chose cash this year
 * is left for them to collect at the office, as is rent on containers paid in cash.
 *
 * <p>A month runs once per kind: the run claims its row in {@code scheduled_withdrawal_runs} first, and the
 * whole run is one transaction. Investors who cannot be paid are recorded with the reason, and keep their balance.
 */
@Service
public class ScheduledWithdrawalService {

	private static final String ENTITY = "SCHEDULED_WITHDRAWAL_RUN";
	private static final TypeReference<List<ScheduledRunResponse.Skipped>> SKIPPED = new TypeReference<>() {
	};

	private final JdbcTemplate jdbc;
	private final WithdrawalRepository withdrawals;
	private final WithdrawalService withdrawalService;
	private final PayoutBatchService batches;
	private final BankAccountService bankAccounts;
	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final LedgerService ledger;
	private final AuditService audit;
	private final WithdrawalSettingsService settings;
	private final ReferralPayoutModeService referralModes;
	private final WithdrawalProperties properties;
	private final MoneyProperties money;
	private final JsonMapper json;
	private final Clock clock;

	public ScheduledWithdrawalService(JdbcTemplate jdbc, WithdrawalRepository withdrawals,
			WithdrawalService withdrawalService, PayoutBatchService batches, BankAccountService bankAccounts,
			UserAccountService accounts, UserProfileService profiles, LedgerService ledger, AuditService audit,
			WithdrawalSettingsService settings, ReferralPayoutModeService referralModes, WithdrawalProperties properties,
			MoneyProperties money, JsonMapper json, Clock clock) {
		this.referralModes = referralModes;
		this.jdbc = jdbc;
		this.withdrawals = withdrawals;
		this.withdrawalService = withdrawalService;
		this.batches = batches;
		this.bankAccounts = bankAccounts;
		this.accounts = accounts;
		this.profiles = profiles;
		this.ledger = ledger;
		this.audit = audit;
		this.settings = settings;
		this.properties = properties;
		this.money = money;
		this.json = json;
		this.clock = clock;
	}

	/** Runs this month's rent and referral withdrawals whose file day has come and that have not run yet. */
	@Transactional
	public List<ScheduledRunResponse> runDue() {
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		YearMonth month = YearMonth.from(today);
		List<ScheduledRunResponse> made = new ArrayList<>();
		for (WithdrawalKind kind : WithdrawalKind.values()) {
			if (!today.isBefore(WithdrawalSettingsService.fileDate(month, settings.withdrawalDay(kind)))) {
				run(kind, month, null).ifPresent(made::add);
			}
		}
		return made;
	}

	/** Runs this month's withdrawals of a kind now, whatever the day; refused when they have already been made. */
	@Transactional
	public ScheduledRunResponse runNow(UUID actorId, WithdrawalKind kind) {
		YearMonth month = YearMonth.from(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
		return run(kind, month, actorId).orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT,
				kind.label() + " withdrawals for " + month + " have already been made"));
	}

	@Transactional(readOnly = true)
	public List<ScheduledRunResponse> recent(int limit) {
		return jdbc.query("""
				SELECT r.*, b.reference AS batch_reference FROM scheduled_withdrawal_runs r
				LEFT JOIN withdrawal_batches b ON b.id = r.batch_id
				ORDER BY r.period DESC, r.kind DESC, r.currency LIMIT ?
				""", run(), limit);
	}

	// ----------------------------------------------------------------------------- internal

	private Optional<ScheduledRunResponse> run(WithdrawalKind kind, YearMonth period, UUID actorId) {
		Currency currency = money.defaultCurrency();
		EarningsBalance from = kind.balance();
		UUID runId = UUID.randomUUID();
		Instant now = clock.instant();
		int claimed = jdbc.update("""
				INSERT INTO scheduled_withdrawal_runs (id, period, currency, kind, run_at, triggered_by) VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (period, currency, kind) DO NOTHING
				""", runId, period.toString(), currency.getCurrencyCode(), kind.name(), Timestamp.from(now), actorId);
		if (claimed == 0) {
			return Optional.empty();
		}

		Money minimum = Money.of(properties.minimumAmount(), currency);
		List<Withdrawal> made = new ArrayList<>();
		List<ScheduledRunResponse.Skipped> skipped = new ArrayList<>();
		for (UUID userId : ledger.investorsWithBalanceAtLeast(from, currency, properties.minimumAmount())) {
			// Referral commission of investors who collect it in cash this year is withdrawn at the office instead.
			if (kind == WithdrawalKind.REFERRAL
					&& referralModes.current(userId).mode() != WithdrawalPayoutMethod.BANK_TRANSFER) {
				continue;
			}
			Optional<PayoutAccount> account = bankAccounts.verifiedPrimaryAccount(userId)
				.flatMap(a -> bankAccounts.payoutAccount(a.id()));
			String reason = ineligible(userId, account, currency);
			if (reason != null) {
				skipped.add(new ScheduledRunResponse.Skipped(userId, reason));
				continue;
			}
			// Serialises with everything else that spends this balance; re-read under the lock.
			Money balance = ledger.lockInvestorBalance(userId, currency, from);
			if (withdrawals.existsByUserIdAndCurrencyAndBalanceAndStatusIn(userId, currency.getCurrencyCode(), from,
					List.of(WithdrawalStatus.FAILED))) {
				skipped.add(new ScheduledRunResponse.Skipped(userId, "An earlier withdrawal failed and is being resolved"));
				continue;
			}
			if (withdrawals.existsByUserIdAndCurrencyAndBalanceAndStatusIn(userId, currency.getCurrencyCode(), from,
					WithdrawalStatus.OPEN)) {
				skipped.add(new ScheduledRunResponse.Skipped(userId, "A withdrawal is already in progress"));
				continue;
			}
			// Whole minor units only; any fraction stays in the balance for next month.
			Money amount = Money.of(balance.amount().setScale(Math.max(currency.getDefaultFractionDigits(), 0),
					RoundingMode.DOWN), currency);
			if (amount.isLessThan(minimum)) {
				continue;
			}
			made.add(withdraw(kind, userId, account.orElseThrow(), amount, period));
		}

		WithdrawalBatch batch = made.isEmpty() ? null : batches.createScheduled(kind, period, made);
		Money total = made.stream().map(Withdrawal::amount).reduce(Money::plus).orElse(Money.zero(currency));
		jdbc.update("""
				UPDATE scheduled_withdrawal_runs
				SET batch_id = ?, withdrawal_count = ?, total_amount = ?, skipped = CAST(? AS jsonb) WHERE id = ?
				""", batch == null ? null : batch.getId(), made.size(), total.amount(), json.writeValueAsString(skipped), runId);
		audit.record(AuditRecord.of(actorId, AuditAction.SCHEDULED_WITHDRAWALS_RUN, ENTITY, runId)
			.withNewValue(Map.of("kind", kind, "period", period.toString(), "withdrawals", made.size(), "total", total.toString(),
					"skipped", skipped.size(), "batch", batch == null ? "" : batch.getReference())));
		return Optional.of(new ScheduledRunResponse(runId, kind, period.toString(), currency.getCurrencyCode(), now, actorId,
				batch == null ? null : batch.getId(), batch == null ? null : batch.getReference(), made.size(),
				MoneyResponse.from(total), skipped));
	}

	private Withdrawal withdraw(WithdrawalKind kind, UUID userId, PayoutAccount account, Money amount, YearMonth period) {
		Withdrawal withdrawal = withdrawals.saveAndFlush(Withdrawal.scheduled("WD-" + withdrawals.nextNumber(), userId,
				amount, "scheduled:" + kind + ":" + period, kind.balance(), account.id(), account.holderName(),
				account.bankName(), account.last4()));
		ledger.post(TransactionType.WITHDRAWAL_RESERVE, withdrawal.getId().toString(),
				kind.label() + " withdrawal " + withdrawal.getReference() + " for " + period, null,
				List.of(Posting.debit(kind.balance().account(), userId, amount),
						Posting.credit(AccountType.WITHDRAWALS_IN_TRANSIT, null, amount)));
		audit.record(AuditRecord.of(null, AuditAction.WITHDRAWAL_REQUESTED, WithdrawalService.ENTITY, withdrawal.getId())
			.withNewValue(Map.of("reference", withdrawal.getReference(), "amount", amount.toString(),
					"bankAccountId", account.id().toString(), "scheduled", kind + " " + period)));
		withdrawalService.publish(KafkaTopics.WITHDRAWAL_APPROVED, "WithdrawalApproved", withdrawal,
				Map.of("scheduled", true));
		return withdrawal;
	}

	/** Why an investor cannot be paid this month, or null when they can. */
	private String ineligible(UUID userId, Optional<PayoutAccount> account, Currency currency) {
		if (accounts.getAccount(userId).status() != UserStatus.ACTIVE) {
			return "The account is not active";
		}
		if (profiles.kycStatusOf(userId) != KycStatus.APPROVED) {
			return "Identity verification is not approved";
		}
		if (account.isEmpty()) {
			return "No verified primary bank account";
		}
		if (!account.get().currency().equals(currency.getCurrencyCode())) {
			return "The primary bank account is in " + account.get().currency();
		}
		return null;
	}

	private RowMapper<ScheduledRunResponse> run() {
		return (rs, i) -> {
			Currency currency = Currency.getInstance(rs.getString("currency"));
			BigDecimal total = rs.getBigDecimal("total_amount");
			return new ScheduledRunResponse(rs.getObject("id", UUID.class), WithdrawalKind.valueOf(rs.getString("kind")),
					rs.getString("period"),
					currency.getCurrencyCode(), rs.getTimestamp("run_at").toInstant(),
					rs.getObject("triggered_by", UUID.class), rs.getObject("batch_id", UUID.class),
					rs.getString("batch_reference"), rs.getInt("withdrawal_count"),
					MoneyResponse.from(Money.of(total, currency)), json.readValue(rs.getString("skipped"), SKIPPED));
		};
	}

}
