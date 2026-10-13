package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.dto.PayoutAccount;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import com.sealease.backend.payment.provider.OfficeProperties;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import com.sealease.backend.withdrawal.dto.WithdrawalPolicy;
import com.sealease.backend.withdrawal.dto.OfficeWithdrawalOption;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalSearchCriteria;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalOrigin;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Withdrawals: what investors see of them, and cash withdrawn at the office.
 *
 * <p>Bank withdrawals are made by the monthly runs ({@link ScheduledWithdrawalService}) and paid in batches
 * ({@link PayoutBatchService}). Cash is withdrawn by finance at the office in front of the investor, from the
 * withdrawal day: rent on containers paid in cash, and referral commission of investors who chose cash this year.
 * Making a withdrawal moves its amount from the investor's balance into "withdrawals in transit" under the lock on
 * the balance's ledger account, so a balance can never be withdrawn twice; rejection or return moves it back
 * ({@link #release}). Lock order: withdrawal, then ledger account.
 */
@Service
public class WithdrawalService {

	static final String ENTITY = "WITHDRAWAL";

	private final WithdrawalRepository withdrawals;
	private final BankAccountService bankAccounts;
	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final LedgerService ledger;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final WithdrawalProperties properties;
	private final OfficeProperties office;
	private final MoneyProperties money;
	private final WithdrawalSettingsService settings;
	private final ReferralPayoutModeService referralModes;
	private final Clock clock;

	public WithdrawalService(WithdrawalRepository withdrawals, BankAccountService bankAccounts,
			UserAccountService accounts, UserProfileService profiles, LedgerService ledger, OutboxPublisher outbox,
			AuditService audit, WithdrawalProperties properties, OfficeProperties office,
			MoneyProperties money, WithdrawalSettingsService settings, ReferralPayoutModeService referralModes, Clock clock) {
		this.settings = settings;
		this.referralModes = referralModes;
		this.withdrawals = withdrawals;
		this.bankAccounts = bankAccounts;
		this.accounts = accounts;
		this.profiles = profiles;
		this.ledger = ledger;
		this.outbox = outbox;
		this.audit = audit;
		this.properties = properties;
		this.office = office;
		this.money = money;
		this.clock = clock;
	}

	public WithdrawalPolicy policy() {
		return new WithdrawalPolicy(properties.minimumAmount(), settings.withdrawalDay(WithdrawalKind.RENT),
				settings.withdrawalDay(WithdrawalKind.REFERRAL), office.address(), office.hours());
	}

	// ----------------------------------------------------------------------- at the office

	/**
	 * What finance may withdraw in cash for an investor at the office today: their whole rent (cash) balance and, if
	 * they chose cash this year, their whole referral balance, each once a month from its withdrawal day.
	 */
	@Transactional(readOnly = true)
	public List<OfficeWithdrawalOption> officeOptions(UUID userId) {
		Currency currency = money.defaultCurrency();
		List<OfficeWithdrawalOption> options = new ArrayList<>();
		for (EarningsBalance balance : List.of(EarningsBalance.RENT_CASH, EarningsBalance.REFERRAL)) {
			Money available = whole(ledger.investorBalance(userId, currency, balance));
			options.add(new OfficeWithdrawalOption(balance, MoneyResponse.from(available),
					refusal(userId, balance, available)));
		}
		return options;
	}

	/**
	 * Finance withdraws an investor's whole balance in cash at the office, in front of them: the withdrawal is ready
	 * to hand over ({@link #handOverCash}). Refused unless {@link #officeOptions} allows it.
	 */
	@Transactional
	public WithdrawalResponse atOffice(UUID actorId, UUID userId, EarningsBalance balance) {
		if (userId.equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot withdraw for yourself");
		}
		if (balance == EarningsBalance.RENT_BANK) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Rent on containers paid by bank is paid into the investor's bank account by the monthly run");
		}
		requireMayWithdraw(userId);
		Currency currency = money.defaultCurrency();
		// Serialises everything that spends this balance; re-checked under the lock.
		Money available = whole(ledger.lockInvestorBalance(userId, currency, balance));
		String refusal = refusal(userId, balance, available);
		if (refusal != null) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, refusal);
		}
		Withdrawal withdrawal = withdrawals.saveAndFlush(Withdrawal.atOffice("WD-" + withdrawals.nextNumber(), userId,
				available, "office:" + UUID.randomUUID(), balance, actorId));
		ledger.post(TransactionType.WITHDRAWAL_RESERVE, withdrawal.getId().toString(),
				"Withdrawal " + withdrawal.getReference() + " in cash at the office", actorId,
				List.of(Posting.debit(balance.account(), userId, available),
						Posting.credit(AccountType.WITHDRAWALS_IN_TRANSIT, null, available)));
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_REQUESTED, ENTITY, withdrawal.getId())
			.withNewValue(Map.of("reference", withdrawal.getReference(), "amount", available.toString(),
					"balance", balance, "payoutMethod", withdrawal.getPayoutMethod(), "atOffice", true)));
		publish(KafkaTopics.WITHDRAWAL_APPROVED, "WithdrawalApproved", withdrawal, Map.of("atOffice", true));
		return WithdrawalResponse.forStaff(withdrawal);
	}

	/** Why this balance cannot be withdrawn in cash today, or null when it can. */
	private String refusal(UUID userId, EarningsBalance balance, Money available) {
		WithdrawalKind kind = balance == EarningsBalance.REFERRAL ? WithdrawalKind.REFERRAL : WithdrawalKind.RENT;
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		int day = settings.withdrawalDay(kind);
		if (balance == EarningsBalance.REFERRAL
				&& referralModes.current(userId).mode() != WithdrawalPayoutMethod.OFFICE_CASH) {
			return "Referral earnings are paid into this investor's bank account this year";
		}
		if (today.getDayOfMonth() < day) {
			return "Cash is withdrawn from the " + ordinal(day) + " of the month";
		}
		if (withdrawals.existsByUserIdAndCurrencyAndBalanceAndStatusIn(userId, available.currency().getCurrencyCode(),
				balance, WithdrawalStatus.OPEN)) {
			return "A withdrawal of this balance is already in progress";
		}
		Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		if (withdrawals.existsByUserIdAndBalanceAndOriginAndCreatedAtGreaterThanEqualAndStatusNot(userId, balance,
				WithdrawalOrigin.OFFICE, monthStart, WithdrawalStatus.REJECTED)) {
			return "This balance was already withdrawn this month";
		}
		if (!available.isPositive()) {
			return "Nothing to withdraw";
		}
		return null;
	}

	/** Whole minor units only; any fraction stays in the balance. */
	private static Money whole(Money amount) {
		int digits = Math.max(amount.currency().getDefaultFractionDigits(), 0);
		return Money.of(amount.amount().setScale(digits, RoundingMode.DOWN), amount.currency());
	}

	private static String ordinal(int day) {
		String suffix = day >= 11 && day <= 13 ? "th" : switch (day % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
		return day + suffix;
	}

	/** The investor's withdrawals requested in {@code [from, to)}, for statements. */
	@Transactional(readOnly = true)
	public List<WithdrawalResponse> forStatement(UUID userId, Instant from, Instant to) {
		return withdrawals.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAt(userId, from, to)
			.stream().map(WithdrawalResponse::forInvestor).toList();
	}

	/** Withdrawals not yet paid out or returned: count and amount per currency. */
	@Transactional(readOnly = true)
	public Map<String, Money> openAmounts() {
		Map<String, Money> totals = new TreeMap<>();
		for (Withdrawal w : withdrawals.findByStatusIn(WithdrawalStatus.OPEN)) {
			totals.merge(w.getCurrency(), w.amount(), Money::plus);
		}
		return totals;
	}

	@Transactional(readOnly = true)
	public long openCount() {
		return withdrawals.findByStatusIn(WithdrawalStatus.OPEN).size();
	}

	/** For support tickets: the reference of one of the user's own withdrawals. */
	@Transactional(readOnly = true)
	public Optional<String> ownedReference(UUID userId, UUID withdrawalId) {
		return withdrawals.findById(withdrawalId).filter(w -> w.getUserId().equals(userId)).map(Withdrawal::getReference);
	}

	@Transactional(readOnly = true)
	public Page<WithdrawalResponse> mine(UUID userId, Pageable pageable) {
		return withdrawals.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(WithdrawalResponse::forInvestor);
	}

	// ------------------------------------------------------------------------------ staff

	/**
	 * Finance hands the cash of an approved office withdrawal to the investor and records it with the receipt
	 * number: the withdrawal is paid, out of "withdrawals in transit". Nobody hands cash to themselves.
	 */
	@Transactional
	public WithdrawalResponse handOverCash(UUID actorId, UUID withdrawalId, String receiptNumber) {
		Withdrawal withdrawal = lock(withdrawalId);
		if (withdrawal.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot pay out your own withdrawal");
		}
		if (!withdrawal.isOfficeCash()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"This withdrawal is paid by bank transfer, in a payout batch");
		}
		if (withdrawal.getStatus() != WithdrawalStatus.APPROVED) {
			throw new BusinessException(ErrorCode.CONFLICT, "This withdrawal is already " + withdrawal.getStatus());
		}
		Money amount = withdrawal.amount();
		String receipt = receiptNumber.strip();
		withdrawal.markPaid(receipt, clock.instant());
		withdrawals.flush();
		ledger.post(TransactionType.WITHDRAWAL_PAYOUT, withdrawal.getId().toString(),
				"Withdrawal " + withdrawal.getReference() + " paid in cash at the office (receipt " + receipt + ")", actorId,
				List.of(Posting.debit(AccountType.WITHDRAWALS_IN_TRANSIT, null, amount),
						Posting.credit(AccountType.RENTAL_CASH, null, amount)));
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_PAID, ENTITY, withdrawalId)
			.withNewValue(Map.of("amount", amount.toString(), "payoutMethod", withdrawal.getPayoutMethod(),
					"payoutReference", receipt)));
		publish(KafkaTopics.WITHDRAWAL_COMPLETED, "WithdrawalPaid", withdrawal, Map.of());
		return WithdrawalResponse.forStaff(withdrawal);
	}

	/** Cancels a cash withdrawal not handed over (e.g. the investor left); the money returns to their balance. */
	@Transactional
	public WithdrawalResponse reject(UUID actorId, UUID withdrawalId, String reason) {
		Withdrawal withdrawal = lock(withdrawalId);
		if (withdrawal.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot decide on your own withdrawal");
		}
		WithdrawalStatus previous = withdrawal.getStatus();
		if (previous != WithdrawalStatus.PENDING_APPROVAL && previous != WithdrawalStatus.APPROVED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, previous == WithdrawalStatus.FAILED
					? "Resolve the failed payment instead: mark it paid or return it to the balance"
					: "This withdrawal is already " + previous);
		}
		withdrawal.reject(actorId, reason.strip(), clock.instant());
		withdrawals.flush();
		release(withdrawal, actorId, "rejected");
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_REJECTED, ENTITY, withdrawalId)
			.withOldValue(Map.of("status", previous))
			.withNewValue(Map.of("status", WithdrawalStatus.REJECTED, "reason", reason.strip())));
		publish(KafkaTopics.WITHDRAWAL_FAILED, "WithdrawalRejected", withdrawal, Map.of("reason", reason.strip()));
		return WithdrawalResponse.forStaff(withdrawal);
	}

	@Transactional(readOnly = true)
	public Page<WithdrawalResponse> search(WithdrawalSearchCriteria criteria, Pageable pageable) {
		return withdrawals.findAll(matching(criteria), pageable).map(WithdrawalResponse::forStaff);
	}

	@Transactional(readOnly = true)
	public WithdrawalResponse detail(UUID withdrawalId) {
		return WithdrawalResponse.forStaff(withdrawals.findById(withdrawalId)
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId)));
	}

	// --------------------------------------------------------------------------- internal

	/** Returns a withdrawal's amount from "in transit" to the investor's balance. Caller's transaction. */
	void release(Withdrawal withdrawal, UUID actorId, String why) {
		Money amount = withdrawal.amount();
		ledger.post(TransactionType.WITHDRAWAL_RELEASE, withdrawal.getId().toString(),
				"Withdrawal " + withdrawal.getReference() + " " + why + ": returned to balance", actorId,
				List.of(Posting.debit(AccountType.WITHDRAWALS_IN_TRANSIT, null, amount),
						Posting.credit(withdrawal.getBalance().account(), withdrawal.getUserId(), amount)));
	}

	void publish(String topic, String type, Withdrawal w, Map<String, ?> extra) {
		Map<String, Object> payload = new HashMap<>(extra);
		payload.put("withdrawalId", w.getId().toString());
		payload.put("reference", w.getReference());
		payload.put("userId", w.getUserId().toString());
		payload.put("amount", w.amount().amount().toPlainString());
		payload.put("currency", w.getCurrency());
		payload.put("status", w.getStatus().name());
		outbox.publish(DomainEvent.of(topic, type, ENTITY, w.getId(), payload));
	}

	private void requireMayWithdraw(UUID userId) {
		if (accounts.getAccount(userId).status() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The account is not active");
		}
		if (profiles.kycStatusOf(userId) != KycStatus.APPROVED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Identity verification is required to withdraw");
		}
	}

	private Withdrawal lock(UUID withdrawalId) {
		return withdrawals.findByIdForUpdate(withdrawalId)
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
	}

	private static Specification<Withdrawal> matching(WithdrawalSearchCriteria c) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (c.status() != null) {
				predicates.add(cb.equal(root.get("status"), c.status()));
			}
			if (c.currency() != null && !c.currency().isBlank()) {
				predicates.add(cb.equal(root.get("currency"), c.currency().strip().toUpperCase(Locale.ROOT)));
			}
			if (c.userId() != null) {
				predicates.add(cb.equal(root.get("userId"), c.userId()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
