package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.dto.PayoutAccount;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.demo.service.DemoAccountService;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import com.sealease.backend.withdrawal.dto.WithdrawalRunResponse.Skipped;
import com.sealease.backend.withdrawal.dto.WithdrawalRunResponse;
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
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Withdrawal runs, made by an admin whenever they choose. A run withdraws every eligible investor's rent and referral
 * balances up to that moment: each balance becomes one withdrawal, its amount moved from the balance into
 * "withdrawals in transit" (in progress). Rent on containers paid by bank and referral commission of investors who
 * chose bank this year go on the run's bank sheet; rent on containers paid in cash at the office and referral
 * commission of investors who chose cash go on its cash sheet. Each sheet is a batch with its Excel file, one row per
 * investor; finance pays it and uploads it back ({@link PayoutBatchService#reconcile}), completing the withdrawals.
 *
 * <p>An investor is left out of a sheet (and keeps the balances) when they cannot be paid that way, when a withdrawal
 * of one of its balances is still open or failed, or when their row would be below the minimum withdrawal. The whole
 * run is one transaction.
 */
@Service
public class WithdrawalRunService {

	private static final String ENTITY = "WITHDRAWAL_RUN";
	private static final TypeReference<List<Skipped>> SKIPPED = new TypeReference<>() {
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
	private final ReferralPayoutModeService referralModes;
	private final WithdrawalSettingsService settings;
	private final MoneyProperties money;
	private final JsonMapper json;
	private final DemoAccountService demos;
	private final Clock clock;

	public WithdrawalRunService(JdbcTemplate jdbc, WithdrawalRepository withdrawals, WithdrawalService withdrawalService,
			PayoutBatchService batches, BankAccountService bankAccounts, UserAccountService accounts,
			UserProfileService profiles, LedgerService ledger, AuditService audit, ReferralPayoutModeService referralModes,
			WithdrawalSettingsService settings, MoneyProperties money, JsonMapper json, DemoAccountService demos,
			Clock clock) {
		this.jdbc = jdbc;
		this.withdrawals = withdrawals;
		this.withdrawalService = withdrawalService;
		this.batches = batches;
		this.bankAccounts = bankAccounts;
		this.accounts = accounts;
		this.profiles = profiles;
		this.ledger = ledger;
		this.audit = audit;
		this.referralModes = referralModes;
		this.settings = settings;
		this.money = money;
		this.json = json;
		this.demos = demos;
		this.clock = clock;
	}

	/**
	 * Withdraws every eligible investor's rent balances (a RENT run) or referral balance (a REFERRAL run) into a bank
	 * sheet and a cash sheet. The other kind is left alone, for its own run.
	 */
	@Transactional
	public WithdrawalRunResponse generate(UUID actorId, WithdrawalKind kind) {
		Currency currency = money.defaultCurrency();
		Money minimum = Money.of(settings.minimumAmount(), currency);
		UUID runId = UUID.randomUUID();
		Long number = jdbc.queryForObject("SELECT nextval('withdrawal_run_number_seq')", Long.class);
		String reference = "WR-" + String.format("%05d", number);
		Instant now = clock.instant();

		// Everyone with something in any balance (at least a paisa), in a stable order.
		TreeSet<UUID> investors = new TreeSet<>();
		BigDecimal smallest = BigDecimal.ONE.movePointLeft(Math.max(currency.getDefaultFractionDigits(), 0));
		for (EarningsBalance balance : kind.balances()) {
			investors.addAll(ledger.investorsWithBalanceAtLeast(balance, currency, smallest));
		}

		Map<WithdrawalPayoutMethod, List<Withdrawal>> made = new EnumMap<>(WithdrawalPayoutMethod.class);
		List<Skipped> skipped = new ArrayList<>();
		for (UUID userId : investors) {
			for (Map.Entry<WithdrawalPayoutMethod, List<EarningsBalance>> sheet : sheets(userId, kind).entrySet()) {
				WithdrawalPayoutMethod method = sheet.getKey();
				Optional<PayoutAccount> account = method == WithdrawalPayoutMethod.BANK_TRANSFER
						? bankAccounts.verifiedPrimaryAccount(userId).flatMap(a -> bankAccounts.payoutAccount(a.id()))
						: Optional.empty();
				String reason = ineligible(userId, method, account, currency);
				if (reason != null) {
					if (hasMoney(userId, currency, sheet.getValue())) {
						skipped.add(new Skipped(userId, method, reason));
					}
					continue;
				}
				// Serialises with everything else that spends these balances; amounts are read under the lock.
				Map<EarningsBalance, Money> amounts = new LinkedHashMap<>();
				for (EarningsBalance balance : sheet.getValue()) {
					if (!whole(ledger.investorBalance(userId, currency, balance)).isPositive()) {
						continue;
					}
					Money available = whole(ledger.lockInvestorBalance(userId, currency, balance));
					if (!available.isPositive()) {
						continue;
					}
					if (withdrawals.existsByUserIdAndCurrencyAndBalanceAndStatusIn(userId, currency.getCurrencyCode(),
							balance, List.of(WithdrawalStatus.FAILED))) {
						skipped.add(new Skipped(userId, method, "An earlier withdrawal failed and is being resolved"));
						continue;
					}
					if (withdrawals.existsByUserIdAndCurrencyAndBalanceAndStatusIn(userId, currency.getCurrencyCode(),
							balance, WithdrawalStatus.OPEN)) {
						skipped.add(new Skipped(userId, method, "A withdrawal is already in progress"));
						continue;
					}
					amounts.put(balance, available);
				}
				Money total = amounts.values().stream().reduce(Money::plus).orElse(Money.zero(currency));
				// Small balances keep growing until the next run.
				if (total.isLessThan(minimum)) {
					continue;
				}
				amounts.forEach((balance, amount) -> made.computeIfAbsent(method, m -> new ArrayList<>())
					.add(withdraw(reference, userId, method, balance, amount, account)));
			}
		}

		WithdrawalBatch bank = batch(made, kind, WithdrawalPayoutMethod.BANK_TRANSFER, reference, actorId);
		WithdrawalBatch cash = batch(made, kind, WithdrawalPayoutMethod.OFFICE_CASH, reference, actorId);
		int bankInvestors = investorsIn(made, WithdrawalPayoutMethod.BANK_TRANSFER);
		int cashInvestors = investorsIn(made, WithdrawalPayoutMethod.OFFICE_CASH);
		Money bankTotal = total(made, WithdrawalPayoutMethod.BANK_TRANSFER, currency);
		Money cashTotal = total(made, WithdrawalPayoutMethod.OFFICE_CASH, currency);
		jdbc.update("""
				INSERT INTO withdrawal_runs (id, reference, kind, currency, run_at, triggered_by, bank_batch_id, cash_batch_id,
				                             bank_count, bank_total, cash_count, cash_total, skipped)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
				""", runId, reference, kind.name(), currency.getCurrencyCode(), Timestamp.from(now), actorId,
				bank == null ? null : bank.getId(), cash == null ? null : cash.getId(), bankInvestors, bankTotal.amount(),
				cashInvestors, cashTotal.amount(), json.writeValueAsString(skipped));
		audit.record(AuditRecord.of(actorId, AuditAction.SCHEDULED_WITHDRAWALS_RUN, ENTITY, runId)
			.withNewValue(Map.of("reference", reference, "kind", kind, "bankInvestors", bankInvestors, "bankTotal", bankTotal.toString(),
					"cashInvestors", cashInvestors, "cashTotal", cashTotal.toString(), "skipped", skipped.size(),
					"bankBatch", bank == null ? "" : bank.getReference(), "cashBatch", cash == null ? "" : cash.getReference())));
		return new WithdrawalRunResponse(runId, reference, kind, currency.getCurrencyCode(), now, actorId,
				bank == null ? null : bank.getId(), bank == null ? null : bank.getReference(), bankInvestors,
				MoneyResponse.from(bankTotal), cash == null ? null : cash.getId(), cash == null ? null : cash.getReference(),
				cashInvestors, MoneyResponse.from(cashTotal), withCodes(skipped));
	}

	@Transactional(readOnly = true)
	public List<WithdrawalRunResponse> recent(int limit) {
		return jdbc.query("""
				SELECT r.*, b.reference AS bank_reference, c.reference AS cash_reference FROM withdrawal_runs r
				LEFT JOIN withdrawal_batches b ON b.id = r.bank_batch_id
				LEFT JOIN withdrawal_batches c ON c.id = r.cash_batch_id
				ORDER BY r.run_at DESC LIMIT ?
				""", run(), limit).stream()
			.map(r -> new WithdrawalRunResponse(r.id(), r.reference(), r.kind(), r.currency(), r.runAt(), r.triggeredBy(),
					r.bankBatchId(), r.bankBatchReference(), r.bankInvestors(), r.bankTotal(), r.cashBatchId(),
					r.cashBatchReference(), r.cashInvestors(), r.cashTotal(), withCodes(r.skipped())))
			.toList();
	}

	/** The left-out investors with their readable IDs ("SL03500"). */
	private List<Skipped> withCodes(List<Skipped> skipped) {
		Map<UUID, UserAccount> people = accounts.getAccounts(skipped.stream().map(Skipped::userId).distinct().toList());
		return skipped.stream().map(k -> {
			UserAccount person = people.get(k.userId());
			return new Skipped(k.userId(), person == null ? null : person.userCode(), k.payoutMethod(), k.reason());
		}).toList();
	}

	// ----------------------------------------------------------------------------- internal

	/**
	 * Which of the run's balances go on which sheet: rent by how the container was paid, referral by the investor's
	 * choice for the year.
	 */
	private Map<WithdrawalPayoutMethod, List<EarningsBalance>> sheets(UUID userId, WithdrawalKind kind) {
		Map<WithdrawalPayoutMethod, List<EarningsBalance>> sheets = new EnumMap<>(WithdrawalPayoutMethod.class);
		if (kind == WithdrawalKind.RENT) {
			sheets.put(WithdrawalPayoutMethod.BANK_TRANSFER, List.of(EarningsBalance.RENT_BANK));
			sheets.put(WithdrawalPayoutMethod.OFFICE_CASH, List.of(EarningsBalance.RENT_CASH));
		}
		else {
			sheets.put(referralModes.current(userId).mode(), List.of(EarningsBalance.REFERRAL));
		}
		return sheets;
	}

	private boolean hasMoney(UUID userId, Currency currency, List<EarningsBalance> balances) {
		return balances.stream().anyMatch(b -> whole(ledger.investorBalance(userId, currency, b)).isPositive());
	}

	private Withdrawal withdraw(String run, UUID userId, WithdrawalPayoutMethod method, EarningsBalance balance,
			Money amount, Optional<PayoutAccount> account) {
		String key = "run:" + run + ":" + balance;
		String reference = "WD-" + withdrawals.nextNumber();
		Withdrawal withdrawal = withdrawals.saveAndFlush(method == WithdrawalPayoutMethod.BANK_TRANSFER
				? Withdrawal.scheduled(reference, userId, amount, key, balance, account.orElseThrow().id(),
						account.get().holderName(), account.get().bankName(), account.get().last4())
				: Withdrawal.scheduledCash(reference, userId, amount, key, balance));
		ledger.post(TransactionType.WITHDRAWAL_RESERVE, withdrawal.getId().toString(),
				balanceLabel(balance) + " withdrawal " + withdrawal.getReference() + " in run " + run, null,
				List.of(Posting.debit(balance.account(), userId, amount),
						Posting.credit(AccountType.WITHDRAWALS_IN_TRANSIT, null, amount)));
		audit.record(AuditRecord.of(null, AuditAction.WITHDRAWAL_REQUESTED, WithdrawalService.ENTITY, withdrawal.getId())
			.withNewValue(Map.of("reference", withdrawal.getReference(), "amount", amount.toString(), "balance", balance,
					"payoutMethod", method, "run", run)));
		withdrawalService.publish(KafkaTopics.WITHDRAWAL_APPROVED, "WithdrawalApproved", withdrawal,
				Map.of("scheduled", true, "run", run));
		return withdrawal;
	}

	private WithdrawalBatch batch(Map<WithdrawalPayoutMethod, List<Withdrawal>> made, WithdrawalKind kind,
			WithdrawalPayoutMethod method, String run, UUID actorId) {
		List<Withdrawal> items = made.getOrDefault(method, List.of());
		return items.isEmpty() ? null : batches.createForRun(kind, method, items, run, actorId);
	}

	private static int investorsIn(Map<WithdrawalPayoutMethod, List<Withdrawal>> made, WithdrawalPayoutMethod method) {
		return (int) made.getOrDefault(method, List.of()).stream().map(Withdrawal::getUserId).distinct().count();
	}

	private static Money total(Map<WithdrawalPayoutMethod, List<Withdrawal>> made, WithdrawalPayoutMethod method,
			Currency currency) {
		return made.getOrDefault(method, List.of()).stream().map(Withdrawal::amount).reduce(Money::plus)
			.orElse(Money.zero(currency));
	}

	/** Why an investor cannot be paid this way, or null when they can. */
	private String ineligible(UUID userId, WithdrawalPayoutMethod method, Optional<PayoutAccount> account,
			Currency currency) {
		if (accounts.getAccount(userId).status() != UserStatus.ACTIVE) {
			return "The account is not active";
		}
		if (demos.isDemo(userId)) {
			return "A demo account is not paid out";
		}
		if (profiles.kycStatusOf(userId) != KycStatus.APPROVED) {
			return "Identity verification is not approved";
		}
		if (method == WithdrawalPayoutMethod.BANK_TRANSFER) {
			if (account.isEmpty()) {
				return "No verified primary bank account";
			}
			if (!account.get().currency().equals(currency.getCurrencyCode())) {
				return "The primary bank account is in " + account.get().currency();
			}
		}
		return null;
	}

	/** Whole minor units only; any fraction stays in the balance for the next run. */
	private static Money whole(Money amount) {
		int digits = Math.max(amount.currency().getDefaultFractionDigits(), 0);
		return Money.of(amount.amount().setScale(digits, RoundingMode.DOWN), amount.currency());
	}

	private static String balanceLabel(EarningsBalance balance) {
		return balance == EarningsBalance.REFERRAL ? "Referral" : "Rent";
	}

	private RowMapper<WithdrawalRunResponse> run() {
		return (rs, i) -> {
			Currency currency = Currency.getInstance(rs.getString("currency"));
			String kind = rs.getString("kind");
			return new WithdrawalRunResponse(rs.getObject("id", UUID.class), rs.getString("reference"),
					kind == null ? null : WithdrawalKind.valueOf(kind), currency.getCurrencyCode(), rs.getTimestamp("run_at").toInstant(), rs.getObject("triggered_by", UUID.class),
					rs.getObject("bank_batch_id", UUID.class), rs.getString("bank_reference"), rs.getInt("bank_count"),
					MoneyResponse.from(Money.of(rs.getBigDecimal("bank_total"), currency)),
					rs.getObject("cash_batch_id", UUID.class), rs.getString("cash_reference"), rs.getInt("cash_count"),
					MoneyResponse.from(Money.of(rs.getBigDecimal("cash_total"), currency)),
					json.readValue(rs.getString("skipped"), SKIPPED));
		};
	}

}
