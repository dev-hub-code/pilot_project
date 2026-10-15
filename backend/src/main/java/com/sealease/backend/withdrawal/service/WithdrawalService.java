package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.ledger.service.AccountType;
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
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalSearchCriteria;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Withdrawals: what investors see of them, and what staff see across investors.
 *
 * <p>Withdrawals are made by withdrawal runs ({@link WithdrawalRunService}) and paid in batches - a bank sheet and a
 * cash sheet ({@link PayoutBatchService}). Making a withdrawal moves its amount from the investor's balance into
 * "withdrawals in transit" under the lock on the balance's ledger account, so a balance can never be withdrawn
 * twice; return moves it back ({@link #release}). Lock order: withdrawal, then ledger account.
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
	private final WithdrawalSettingsService settings;
	private final OfficeProperties office;
	private final MoneyProperties money;
	private final Clock clock;

	public WithdrawalService(WithdrawalRepository withdrawals, BankAccountService bankAccounts,
			UserAccountService accounts, UserProfileService profiles, LedgerService ledger, OutboxPublisher outbox,
			AuditService audit, WithdrawalSettingsService settings, OfficeProperties office,
			MoneyProperties money, Clock clock) {
		this.withdrawals = withdrawals;
		this.bankAccounts = bankAccounts;
		this.accounts = accounts;
		this.profiles = profiles;
		this.ledger = ledger;
		this.outbox = outbox;
		this.audit = audit;
		this.settings = settings;
		this.office = office;
		this.money = money;
		this.clock = clock;
	}

	public WithdrawalPolicy policy() {
		return new WithdrawalPolicy(settings.minimumAmount(), office.address(), office.hours());
	}

	/**
	 * The investor's withdrawals per currency: in progress (withdrawn from their balance, not yet paid out; including
	 * failed payments being resolved) and paid out over all time.
	 */
	@Transactional(readOnly = true)
	public InvestorTotals totals(UUID userId) {
		Map<String, Money> inProgress = new TreeMap<>();
		Map<String, Money> withdrawn = new TreeMap<>();
		for (Object[] row : withdrawals.totalsByStatus(userId)) {
			String currency = (String) row[0];
			WithdrawalStatus status = (WithdrawalStatus) row[1];
			Money amount = Money.of((BigDecimal) row[2], java.util.Currency.getInstance(currency));
			if (status.isOpen()) {
				inProgress.merge(currency, amount, Money::plus);
			}
			else if (status == WithdrawalStatus.PAID) {
				withdrawn.merge(currency, amount, Money::plus);
			}
		}
		return new InvestorTotals(List.copyOf(inProgress.values()), List.copyOf(withdrawn.values()));
	}

	/** @see #totals */
	public record InvestorTotals(List<Money> inProgress, List<Money> withdrawn) {
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
			if (c.q() != null && !c.q().isBlank()) {
				Predicate reference = cb.equal(cb.upper(root.get("reference")), c.q().strip().toUpperCase(Locale.ROOT));
				predicates.add(c.qInvestorId() == null ? reference : cb.or(reference, cb.equal(root.get("userId"), c.qInvestorId())));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
