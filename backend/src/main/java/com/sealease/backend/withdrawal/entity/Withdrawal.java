package com.sealease.backend.withdrawal.entity;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.persistence.BaseEntity;
import com.sealease.backend.ledger.service.EarningsBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
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

	/** The finance member who took this finance task (paying it); only they may mark it paid or failed. */
	@Column(name = "task_assignee_id")
	private UUID taskAssigneeId;

	@Column(name = "task_assigned_at")
	private Instant taskAssignedAt;

	/** When the finance task was first marked paid or failed. */
	@Column(name = "task_done_at")
	private Instant taskDoneAt;

	/** Charges taken by the company, fixed when the withdrawal is made. */
	@Column(name = "fee_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal feeAmount = BigDecimal.ZERO;

	/** Tax withheld, fixed when the withdrawal is made. */
	@Column(name = "tax_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal taxAmount = BigDecimal.ZERO;

	/** What is paid to the investor: the amount less charges and tax. */
	@Column(name = "net_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal netAmount;

	/** The reviewer who took the request; only they may make the first decision on it. */
	@Column(name = "review_assignee_id")
	private UUID reviewAssigneeId;

	@Column(name = "review_assigned_at")
	private Instant reviewAssignedAt;

	/** Why it is on hold (shown to the investor when {@link #infoRequested}). */
	@Column(name = "hold_reason", length = 500)
	private String holdReason;

	/** On hold waiting for the investor to send more information. */
	@Column(name = "info_requested", nullable = false)
	private boolean infoRequested;

	@Column(name = "status_changed_at", nullable = false)
	private Instant statusChangedAt;

	@Column(name = "payment_initiated_by")
	private UUID paymentInitiatedBy;

	@Column(name = "payment_initiated_at")
	private Instant paymentInitiatedAt;

	/** The payout provider the payment was handed to, e.g. BANK_SHEET. */
	@Column(name = "payout_provider", length = 30)
	private String payoutProvider;

	protected Withdrawal() {
	}

	public Withdrawal(String reference, UUID userId, Money amount, String idempotencyKey, EarningsBalance balance,
			UUID bankAccountId, String bankHolderName, String bankName, String bankAccountLast4, int requiredApprovals) {
		this.reference = reference;
		this.userId = userId;
		this.amount = amount.amount();
		this.netAmount = amount.amount();
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

	/**
	 * An online investor's own request for a bank payment, SUBMITTED for review. {@code amount} is taken from the
	 * wallet; {@code fee} and {@code tax} come out of it, and the rest is paid.
	 */
	public static Withdrawal requested(String reference, UUID userId, Money amount, Money fee, Money tax,
			String idempotencyKey, EarningsBalance balance, UUID bankAccountId, String bankHolderName, String bankName,
			String bankAccountLast4, int requiredApprovals, Instant now) {
		Withdrawal w = new Withdrawal(reference, userId, amount, idempotencyKey, balance, bankAccountId,
				bankHolderName, bankName, bankAccountLast4, requiredApprovals);
		w.origin = WithdrawalOrigin.REQUESTED;
		w.status = WithdrawalStatus.SUBMITTED;
		w.statusChangedAt = now;
		w.charge(fee, tax);
		return w;
	}

	/** An online investor's own request to collect cash at the office, SUBMITTED for review. */
	public static Withdrawal requestedCash(String reference, UUID userId, Money amount, Money fee, Money tax,
			String idempotencyKey, EarningsBalance balance, int requiredApprovals, Instant now) {
		Withdrawal w = officeCash(reference, userId, amount, idempotencyKey, balance, requiredApprovals);
		w.origin = WithdrawalOrigin.REQUESTED;
		w.status = WithdrawalStatus.SUBMITTED;
		w.statusChangedAt = now;
		w.charge(fee, tax);
		return w;
	}

	private void charge(Money fee, Money tax) {
		BigDecimal net = amount.subtract(fee.amount()).subtract(tax.amount());
		if (fee.amount().signum() < 0 || tax.amount().signum() < 0 || net.signum() <= 0) {
			throw new IllegalArgumentException("Charges must leave something to pay");
		}
		this.feeAmount = fee.amount();
		this.taxAmount = tax.amount();
		this.netAmount = net;
	}

	@PrePersist
	void stampStatus() {
		if (statusChangedAt == null) {
			statusChangedAt = Instant.now();
		}
	}

	/**
	 * Moves to {@code next} if the lifecycle allows it from the current status ({@link WithdrawalStatus#canMoveTo}).
	 *
	 * @return the status it was in
	 */
	private WithdrawalStatus moveTo(WithdrawalStatus next, Instant now) {
		if (!status.canMoveTo(next)) {
			throw new BusinessException(ErrorCode.CONFLICT, "Withdrawal " + reference + " is " + status
					+ " and cannot become " + next);
		}
		WithdrawalStatus before = status;
		status = next;
		statusChangedAt = now;
		if (next.isFinal()) {
			closedAt = now;
		}
		return before;
	}

	/** A withdrawal in cash at the office made by a withdrawal run: goes straight to the run's cash batch. */
	public static Withdrawal scheduledCash(String reference, UUID userId, Money amount, String idempotencyKey,
			EarningsBalance balance) {
		Withdrawal w = officeCash(reference, userId, amount, idempotencyKey, balance, 1);
		w.origin = WithdrawalOrigin.SCHEDULED;
		w.status = WithdrawalStatus.APPROVED;
		return w;
	}

	/** A reviewer takes a submitted (or resumed) request: SUBMITTED → UNDER_REVIEW. */
	public WithdrawalStatus startReview(UUID reviewerId, Instant now) {
		WithdrawalStatus before = moveTo(WithdrawalStatus.UNDER_REVIEW, now);
		reviewAssigneeId = reviewerId;
		reviewAssignedAt = now;
		return before;
	}

	/** Back from hold to the reviewer who holds it: ON_HOLD → UNDER_REVIEW. */
	public WithdrawalStatus resume(Instant now) {
		WithdrawalStatus before = moveTo(WithdrawalStatus.UNDER_REVIEW, now);
		holdReason = null;
		infoRequested = false;
		return before;
	}

	/** Paused with a reason; {@code infoRequested} when the investor is asked for more information. */
	public WithdrawalStatus hold(String reason, boolean infoRequested, Instant now) {
		WithdrawalStatus before = moveTo(WithdrawalStatus.ON_HOLD, now);
		this.holdReason = reason;
		this.infoRequested = infoRequested;
		return before;
	}

	/**
	 * Records an approval: APPROVED when it was the last one needed, otherwise PENDING_APPROVAL for a second, different
	 * approver.
	 *
	 * @return the status it was in
	 */
	public WithdrawalStatus approve(UUID approverId, Instant now) {
		if (wasApprovedBy(approverId)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A second approval must come from someone else");
		}
		WithdrawalStatus next = approvals() + 1 >= requiredApprovals ? WithdrawalStatus.APPROVED
				: WithdrawalStatus.PENDING_APPROVAL;
		WithdrawalStatus before = moveTo(next, now);
		if (firstApprovedBy == null) {
			firstApprovedBy = approverId;
			firstApprovedAt = now;
		}
		else {
			secondApprovedBy = approverId;
			secondApprovedAt = now;
		}
		return before;
	}

	public WithdrawalStatus reject(UUID actorId, String reason, Instant now) {
		WithdrawalStatus before = moveTo(WithdrawalStatus.REJECTED, now);
		rejectedBy = actorId;
		rejectionReason = reason;
		holdReason = null;
		infoRequested = false;
		return before;
	}

	public WithdrawalStatus cancel(Instant now) {
		WithdrawalStatus before = moveTo(WithdrawalStatus.CANCELLED, now);
		holdReason = null;
		infoRequested = false;
		return before;
	}

	/** Payment initiated: handed to the payout provider, which puts it in a batch. */
	public void initiatePayment(UUID actorId, String provider, Instant now) {
		if (status != WithdrawalStatus.APPROVED) {
			throw new BusinessException(ErrorCode.CONFLICT, "Only approved withdrawals are paid; " + reference + " is "
					+ status);
		}
		paymentInitiatedBy = actorId;
		paymentInitiatedAt = now;
		payoutProvider = provider;
	}

	public void addToBatch(UUID batchId, Instant now) {
		moveTo(WithdrawalStatus.BATCHED, now);
		this.batchId = batchId;
	}

	public void removeFromBatch(Instant now) {
		moveTo(WithdrawalStatus.APPROVED, now);
		this.batchId = null;
	}

	public void markProcessing(Instant now) {
		moveTo(WithdrawalStatus.PROCESSING, now);
	}

	public WithdrawalStatus markPaid(String payoutReference, Instant now) {
		WithdrawalStatus before = moveTo(WithdrawalStatus.PAID, now);
		this.payoutReference = payoutReference;
		return before;
	}

	/** The bank did not pay it: the money stays held until finance resolves it ({@link #markReturned}, or paid by hand). */
	public void markFailed(String reason, Instant now) {
		moveTo(WithdrawalStatus.FAILED, now);
		failureReason = reason;
	}

	/** A failed payment given up: the money goes back to the investor's balance. */
	public void markReturned(Instant now) {
		moveTo(WithdrawalStatus.RETURNED, now);
	}

	/** A reviewer takes a request already under review but unassigned (back from hold with nobody on it). */
	public void assignReview(UUID reviewerId, Instant now) {
		if (status != WithdrawalStatus.UNDER_REVIEW) {
			throw new BusinessException(ErrorCode.CONFLICT, "Withdrawal " + reference + " is " + status);
		}
		reviewAssigneeId = reviewerId;
		reviewAssignedAt = now;
	}

	/** The reviewer gives the request back to the queue. */
	public void releaseReview() {
		reviewAssigneeId = null;
		reviewAssignedAt = null;
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

	/** What is taken from the wallet. */
	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
	}

	public Money fee() {
		return Money.of(feeAmount, Currency.getInstance(currency));
	}

	public Money tax() {
		return Money.of(taxAmount, Currency.getInstance(currency));
	}

	/** What is paid out: the amount less charges and tax. */
	public Money net() {
		return Money.of(netAmount, Currency.getInstance(currency));
	}

	public UUID getReviewAssigneeId() {
		return reviewAssigneeId;
	}

	public Instant getReviewAssignedAt() {
		return reviewAssignedAt;
	}

	public String getHoldReason() {
		return holdReason;
	}

	public boolean isInfoRequested() {
		return infoRequested;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

	public UUID getPaymentInitiatedBy() {
		return paymentInitiatedBy;
	}

	public Instant getPaymentInitiatedAt() {
		return paymentInitiatedAt;
	}

	public String getPayoutProvider() {
		return payoutProvider;
	}

	public String getReference() {
		return reference;
	}

	/** A finance member takes the task of paying this withdrawal. */
	public void takeTask(UUID assignee, Instant now) {
		this.taskAssigneeId = assignee;
		this.taskAssignedAt = now;
	}

	/** The task goes back to the queue, for another finance member to take. */
	public void releaseTask() {
		this.taskAssigneeId = null;
		this.taskAssignedAt = null;
	}

	/** The finance task was marked paid or failed; the first time counts. */
	public void taskDone(Instant now) {
		if (taskDoneAt == null) {
			taskDoneAt = now;
		}
	}

	public UUID getTaskAssigneeId() {
		return taskAssigneeId;
	}

	public Instant getTaskAssignedAt() {
		return taskAssignedAt;
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
