package com.sealease.backend.withdrawal.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.persistence.BaseEntity;
import com.sealease.backend.ledger.service.EarningsBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/** An investor's request to be paid part of their earnings balance into a verified bank account. */
@Entity
@Table(name = "withdrawals")
public class Withdrawal extends BaseEntity {

	@Column(name = "reference", nullable = false, updatable = false, length = 20)
	private String reference;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private WithdrawalStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "origin", nullable = false, updatable = false, length = 20)
	private WithdrawalOrigin origin;

	@Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "payout_method", nullable = false, updatable = false, length = 20)
	private WithdrawalPayoutMethod payoutMethod;

	/** The earnings balance the money comes from (and returns to, if the withdrawal does not go ahead). */
	@Enumerated(EnumType.STRING)
	@Column(name = "balance", nullable = false, updatable = false, length = 20)
	private EarningsBalance balance;

	/** The bank account paid into; {@code null} (with the details below) when collected in cash at the office. */
	@Column(name = "bank_account_id", updatable = false)
	private UUID bankAccountId;

	/** The finance user who made a withdrawal at the office; null otherwise. */
	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	/** The support ticket opened when its bank payment failed. */
	@Column(name = "ticket_id")
	private UUID ticketId;

	@Column(name = "bank_holder_name", updatable = false, length = 140)
	private String bankHolderName;

	@Column(name = "bank_name", updatable = false, length = 140)
	private String bankName;

	@Column(name = "bank_account_last4", updatable = false, length = 4)
	private String bankAccountLast4;

	@Column(name = "required_approvals", nullable = false, updatable = false)
	private short requiredApprovals;

	@Column(name = "first_approved_by")
	private UUID firstApprovedBy;

	@Column(name = "first_approved_at")
	private Instant firstApprovedAt;

	@Column(name = "second_approved_by")
	private UUID secondApprovedBy;

	@Column(name = "second_approved_at")
	private Instant secondApprovedAt;

	@Column(name = "rejected_by")
	private UUID rejectedBy;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	@Column(name = "batch_id")
	private UUID batchId;

	@Column(name = "payout_reference", length = 100)
	private String payoutReference;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected Withdrawal() {
	}

	public Withdrawal(String reference, UUID userId, Money amount, String idempotencyKey, EarningsBalance balance,
			UUID bankAccountId, String bankHolderName, String bankName, String bankAccountLast4, int requiredApprovals) {
		this.reference = reference;
		this.userId = userId;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.idempotencyKey = idempotencyKey;
		this.bankAccountId = bankAccountId;
		this.bankHolderName = bankHolderName;
		this.bankName = bankName;
		this.bankAccountLast4 = bankAccountLast4;
		this.requiredApprovals = (short) requiredApprovals;
		this.origin = WithdrawalOrigin.INVESTOR;
		this.status = WithdrawalStatus.PENDING_APPROVAL;
		this.payoutMethod = WithdrawalPayoutMethod.BANK_TRANSFER;
		this.balance = balance;
	}

	/** An investor's withdrawal to be collected in cash at the office: no bank account, never batched. */
	public static Withdrawal officeCash(String reference, UUID userId, Money amount, String idempotencyKey,
			EarningsBalance balance, int requiredApprovals) {
		Withdrawal w = new Withdrawal(reference, userId, amount, idempotencyKey, balance, null, null, null, null,
				requiredApprovals);
		w.payoutMethod = WithdrawalPayoutMethod.OFFICE_CASH;
		return w;
	}

	public boolean isOfficeCash() {
		return payoutMethod == WithdrawalPayoutMethod.OFFICE_CASH;
	}

	public WithdrawalPayoutMethod getPayoutMethod() {
		return payoutMethod;
	}

	public EarningsBalance getBalance() {
		return balance;
	}

	/** Cash withdrawn by finance at the office in front of the investor: ready to hand over. */
	public static Withdrawal atOffice(String reference, UUID userId, Money amount, String idempotencyKey,
			EarningsBalance balance, UUID createdBy) {
		Withdrawal w = officeCash(reference, userId, amount, idempotencyKey, balance, 1);
		w.origin = WithdrawalOrigin.OFFICE;
		w.createdBy = createdBy;
		w.status = WithdrawalStatus.APPROVED;
		return w;
	}

	/** A withdrawal to a bank account made by a withdrawal run: approved by the run, so it goes straight to a batch. */
	public static Withdrawal scheduled(String reference, UUID userId, Money amount, String idempotencyKey,
			EarningsBalance balance, UUID bankAccountId, String bankHolderName, String bankName, String bankAccountLast4) {
		Withdrawal w = new Withdrawal(reference, userId, amount, idempotencyKey, balance, bankAccountId,
				bankHolderName, bankName, bankAccountLast4, 1);
		w.origin = WithdrawalOrigin.SCHEDULED;
		w.status = WithdrawalStatus.APPROVED;
		return w;
	}

	/** A withdrawal in cash at the office made by a withdrawal run: goes straight to the run's cash batch. */
	public static Withdrawal scheduledCash(String reference, UUID userId, Money amount, String idempotencyKey,
			EarningsBalance balance) {
		Withdrawal w = officeCash(reference, userId, amount, idempotencyKey, balance, 1);
		w.origin = WithdrawalOrigin.SCHEDULED;
		w.status = WithdrawalStatus.APPROVED;
		return w;
	}

	/** @return {@code true} when this approval was the last one needed */
	public boolean approve(UUID approverId, Instant now) {
		if (firstApprovedBy == null) {
			firstApprovedBy = approverId;
			firstApprovedAt = now;
		}
		else {
			secondApprovedBy = approverId;
			secondApprovedAt = now;
		}
		if (approvals() >= requiredApprovals) {
			status = WithdrawalStatus.APPROVED;
			return true;
		}
		return false;
	}

	public void reject(UUID actorId, String reason, Instant now) {
		status = WithdrawalStatus.REJECTED;
		rejectedBy = actorId;
		rejectionReason = reason;
		closedAt = now;
	}

	public void cancel(Instant now) {
		status = WithdrawalStatus.CANCELLED;
		closedAt = now;
	}

	public void addToBatch(UUID batchId) {
		this.batchId = batchId;
		status = WithdrawalStatus.BATCHED;
	}

	public void removeFromBatch() {
		this.batchId = null;
		status = WithdrawalStatus.APPROVED;
	}

	public void markProcessing() {
		status = WithdrawalStatus.PROCESSING;
	}

	public void markPaid(String payoutReference, Instant now) {
		status = WithdrawalStatus.PAID;
		this.payoutReference = payoutReference;
		closedAt = now;
	}

	/** The bank did not pay it: the money stays held until finance resolves it ({@link #markReturned}, or paid by hand). */
	public void markFailed(String reason) {
		status = WithdrawalStatus.FAILED;
		failureReason = reason;
	}

	/** A failed payment given up: the money goes back to the investor's balance. */
	public void markReturned(Instant now) {
		status = WithdrawalStatus.RETURNED;
		closedAt = now;
	}

	public void linkTicket(UUID ticketId) {
		this.ticketId = ticketId;
	}

	public int approvals() {
		return (firstApprovedBy == null ? 0 : 1) + (secondApprovedBy == null ? 0 : 1);
	}

	public boolean wasApprovedBy(UUID userId) {
		return userId.equals(firstApprovedBy) || userId.equals(secondApprovedBy);
	}

	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
	}

	public String getReference() {
		return reference;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getCurrency() {
		return currency;
	}

	public WithdrawalOrigin getOrigin() {
		return origin;
	}

	public WithdrawalStatus getStatus() {
		return status;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public UUID getBankAccountId() {
		return bankAccountId;
	}

	public String getBankHolderName() {
		return bankHolderName;
	}

	public String getBankName() {
		return bankName;
	}

	public String getBankAccountLast4() {
		return bankAccountLast4;
	}

	public int getRequiredApprovals() {
		return requiredApprovals;
	}

	public UUID getFirstApprovedBy() {
		return firstApprovedBy;
	}

	public Instant getFirstApprovedAt() {
		return firstApprovedAt;
	}

	public UUID getSecondApprovedBy() {
		return secondApprovedBy;
	}

	public Instant getSecondApprovedAt() {
		return secondApprovedAt;
	}

	public UUID getRejectedBy() {
		return rejectedBy;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public UUID getBatchId() {
		return batchId;
	}

	public String getPayoutReference() {
		return payoutReference;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public UUID getTicketId() {
		return ticketId;
	}

}
