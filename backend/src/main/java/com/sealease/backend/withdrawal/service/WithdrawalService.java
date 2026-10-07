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
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import com.sealease.backend.withdrawal.dto.WithdrawalPolicy;
import com.sealease.backend.withdrawal.dto.WithdrawalRequest;
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

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Withdrawal requests and their approval.
 *
 * <p>Requesting moves the amount from the investor's earnings into "withdrawals in transit" under
 * the lock on the investor's ledger account, so a balance can never be withdrawn twice. Rejection,
 * cancellation and a failed payout move it back ({@link #release}); a payout moves it out
 * ({@link PayoutBatchService}). Lock order: withdrawal, then ledger account.
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
	private final Clock clock;

	public WithdrawalService(WithdrawalRepository withdrawals, BankAccountService bankAccounts,
			UserAccountService accounts, UserProfileService profiles, LedgerService ledger, OutboxPublisher outbox,
			AuditService audit, WithdrawalProperties properties, Clock clock) {
		this.withdrawals = withdrawals;
		this.bankAccounts = bankAccounts;
		this.accounts = accounts;
		this.profiles = profiles;
		this.ledger = ledger;
		this.outbox = outbox;
		this.audit = audit;
		this.properties = properties;
		this.clock = clock;
	}

	public WithdrawalPolicy policy() {
		return new WithdrawalPolicy(properties.minimumAmount(), properties.dualApprovalThreshold());
	}

	// --------------------------------------------------------------------------- investor

	/** Requests a payout. Idempotent per key: a retry returns the original request. */
	@Transactional
	public WithdrawalResponse request(UUID userId, WithdrawalRequest request, String idempotencyKey) {
		String key = IdempotencyKey.require(idempotencyKey);
		Optional<Withdrawal> replay = withdrawals.findByUserIdAndIdempotencyKey(userId, key);
		if (replay.isPresent()) {
			Withdrawal original = replay.get();
			if (!original.getBankAccountId().equals(request.bankAccountId())
					|| original.amount().amount().compareTo(request.amount()) != 0) {
				throw new BusinessException(ErrorCode.CONFLICT,
						IdempotencyKey.HEADER + " was already used for a different withdrawal");
			}
			return WithdrawalResponse.forInvestor(original);
		}

		PayoutAccount account = bankAccounts.payoutAccount(request.bankAccountId())
			.filter(a -> a.userId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Bank account", request.bankAccountId()));
		if (!account.isVerified()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Withdrawals are paid only to verified bank accounts");
		}
		requireMayWithdraw(userId);
		Currency currency = Currency.getInstance(account.currency());
		int digits = Math.max(currency.getDefaultFractionDigits(), 0);
		if (request.amount().stripTrailingZeros().scale() > digits) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Amounts in " + currency.getCurrencyCode() + " allow at most " + digits + " decimals");
		}
		Money amount = Money.of(request.amount(), currency);
		Money minimum = Money.of(properties.minimumAmount(), currency);
		if (amount.isLessThan(minimum)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The minimum withdrawal is " + minimum.display());
		}

		// Serialises everything that spends this balance; re-checked under the lock.
		Money balance = ledger.lockInvestorBalance(userId, currency);
		if (withdrawals.existsByUserIdAndCurrencyAndStatusIn(userId, currency.getCurrencyCode(), WithdrawalStatus.OPEN)) {
			throw new BusinessException(ErrorCode.CONFLICT,
					"You already have a " + currency.getCurrencyCode() + " withdrawal in progress");
		}
		if (amount.isGreaterThan(balance)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Your available balance is " + balance.display());
		}
		int approvals = amount.isGreaterThan(Money.of(properties.dualApprovalThreshold(), currency)) ? 2 : 1;
		Withdrawal withdrawal = withdrawals.saveAndFlush(new Withdrawal("WD-" + withdrawals.nextNumber(), userId, amount,
				key, account.id(), account.holderName(), account.bankName(), account.last4(), approvals));
		ledger.post(TransactionType.WITHDRAWAL_RESERVE, withdrawal.getId().toString(),
				"Withdrawal " + withdrawal.getReference() + " requested", userId,
				List.of(Posting.debit(AccountType.INVESTOR_EARNINGS, userId, amount),
						Posting.credit(AccountType.WITHDRAWALS_IN_TRANSIT, null, amount)));

		audit.record(AuditRecord.of(userId, AuditAction.WITHDRAWAL_REQUESTED, ENTITY, withdrawal.getId())
			.withNewValue(Map.of("reference", withdrawal.getReference(), "amount", amount.toString(),
					"bankAccountId", account.id().toString(), "requiredApprovals", approvals)));
		publish(KafkaTopics.WITHDRAWAL_REQUESTED, "WithdrawalRequested", withdrawal, Map.of());
		return WithdrawalResponse.forInvestor(withdrawal);
	}

	/** The investor withdraws their request while it awaits approval; the money returns to their balance. */
	@Transactional
	public WithdrawalResponse cancel(UUID userId, UUID withdrawalId) {
		Withdrawal withdrawal = lock(withdrawalId);
		if (!withdrawal.getUserId().equals(userId)) {
			throw new ResourceNotFoundException("Withdrawal", withdrawalId);
		}
		if (withdrawal.getStatus() != WithdrawalStatus.PENDING_APPROVAL) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Only withdrawals awaiting approval can be cancelled");
		}
		withdrawal.cancel(clock.instant());
		withdrawals.flush();
		release(withdrawal, userId, "cancelled");
		audit.record(AuditRecord.of(userId, AuditAction.WITHDRAWAL_CANCELLED, ENTITY, withdrawalId)
			.withOldValue(Map.of("status", WithdrawalStatus.PENDING_APPROVAL)));
		publish(KafkaTopics.WITHDRAWAL_FAILED, "WithdrawalCancelled", withdrawal, Map.of());
		return WithdrawalResponse.forInvestor(withdrawal);
	}

	@Transactional(readOnly = true)
	public Page<WithdrawalResponse> mine(UUID userId, Pageable pageable) {
		return withdrawals.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(WithdrawalResponse::forInvestor);
	}

	// ------------------------------------------------------------------------------ staff

	/**
	 * One approval; amounts above the threshold need a second, from someone else. The final approval
	 * re-checks that the investor and their bank account still qualify.
	 */
	@Transactional
	public WithdrawalResponse approve(UUID actorId, UUID withdrawalId) {
		Withdrawal withdrawal = lock(withdrawalId);
		if (withdrawal.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot approve your own withdrawal");
		}
		if (withdrawal.getStatus() != WithdrawalStatus.PENDING_APPROVAL) {
			throw new BusinessException(ErrorCode.CONFLICT, "This withdrawal is already " + withdrawal.getStatus());
		}
		if (withdrawal.wasApprovedBy(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN,
					"You have already approved this withdrawal; a second approver is needed");
		}
		boolean last = withdrawal.approvals() + 1 >= withdrawal.getRequiredApprovals();
		if (last) {
			requireMayWithdraw(withdrawal.getUserId());
			if (!bankAccounts.payoutAccount(withdrawal.getBankAccountId()).map(PayoutAccount::isVerified).orElse(false)) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"The investor's bank account is no longer verified; reject this withdrawal");
			}
		}
		withdrawal.approve(actorId, clock.instant());
		withdrawals.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_APPROVED, ENTITY, withdrawalId)
			.withNewValue(Map.of("approval", withdrawal.approvals() + " of " + withdrawal.getRequiredApprovals(),
					"status", withdrawal.getStatus())));
		if (withdrawal.getStatus() == WithdrawalStatus.APPROVED) {
			publish(KafkaTopics.WITHDRAWAL_APPROVED, "WithdrawalApproved", withdrawal, Map.of());
		}
		return WithdrawalResponse.forStaff(withdrawal);
	}

	/** Refuses a withdrawal that has not been batched yet; the money returns to the investor. */
	@Transactional
	public WithdrawalResponse reject(UUID actorId, UUID withdrawalId, String reason) {
		Withdrawal withdrawal = lock(withdrawalId);
		if (withdrawal.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot decide on your own withdrawal");
		}
		WithdrawalStatus previous = withdrawal.getStatus();
		if (previous != WithdrawalStatus.PENDING_APPROVAL && previous != WithdrawalStatus.APPROVED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, previous == WithdrawalStatus.BATCHED
					? "Cancel its batch first, then reject the withdrawal"
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
						Posting.credit(AccountType.INVESTOR_EARNINGS, withdrawal.getUserId(), amount)));
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
