package com.sealease.backend.withdrawal.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.persistence.BaseEntity;
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

	@Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "bank_account_id", nullable = false, updatable = false)
	private UUID bankAccountId;

	@Column(name = "bank_holder_name", nullable = false, updatable = false, length = 140)
	private String bankHolderName;

	@Column(name = "bank_name", nullable = false, updatable = false, length = 140)
	private String bankName;

	@Column(name = "bank_account_last4", nullable = false, updatable = false, length = 4)
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

	public Withdrawal(String reference, UUID userId, Money amount, String idempotencyKey, UUID bankAccountId,
			String bankHolderName, String bankName, String bankAccountLast4, int requiredApprovals) {
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
		this.status = WithdrawalStatus.PENDING_APPROVAL;
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

	public void markFailed(String reason, Instant now) {
		status = WithdrawalStatus.FAILED;
		failureReason = reason;
		closedAt = now;
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

}
