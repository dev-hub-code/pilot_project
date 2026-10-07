package com.sealease.backend.payment.entity;

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

/** One attempt to pay an order, through one provider. */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

	@Column(name = "order_id", nullable = false, updatable = false)
	private UUID orderId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "method", nullable = false, updatable = false, length = 20)
	private PaymentMethod method;

	@Column(name = "provider", nullable = false, updatable = false, length = 20)
	private String provider;

	@Column(name = "provider_reference", nullable = false, updatable = false, length = 100)
	private String providerReference;

	@Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PaymentStatus status;

	@Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	@Column(name = "external_reference", length = 100)
	private String externalReference;

	@Column(name = "confirmed_by")
	private UUID confirmedBy;

	@Column(name = "settled_at")
	private Instant settledAt;

	@Column(name = "refunded_by")
	private UUID refundedBy;

	@Column(name = "refunded_at")
	private Instant refundedAt;

	@Column(name = "refund_reference", length = 100)
	private String refundReference;

	@Column(name = "refund_reason", length = 500)
	private String refundReason;

	@Column(name = "company_bank_account_id")
	private UUID companyBankAccountId;

	@Enumerated(EnumType.STRING)
	@Column(name = "deposit_mode", length = 20)
	private DepositMode depositMode;

	@Column(name = "deposit_reference", length = 100)
	private String depositReference;

	@Column(name = "deposit_submitted_at")
	private Instant depositSubmittedAt;

	protected Payment() {
	}

	public Payment(UUID orderId, UUID userId, PaymentMethod method, String provider, String providerReference,
			Money amount, String idempotencyKey) {
		this.orderId = orderId;
		this.userId = userId;
		this.method = method;
		this.provider = provider;
		this.providerReference = providerReference;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.status = PaymentStatus.PENDING;
		this.idempotencyKey = idempotencyKey;
	}

	/**
	 * Records that the money arrived. {@code accepted} says whether it pays the order; otherwise it
	 * must be returned to the investor.
	 */
	public void settle(boolean accepted, String externalReference, UUID confirmedBy, String note, Instant now) {
		if (status.isSettled()) {
			throw new IllegalStateException("Payment already settled");
		}
		this.status = accepted ? PaymentStatus.SUCCEEDED : PaymentStatus.REFUND_REQUIRED;
		this.externalReference = externalReference;
		this.confirmedBy = confirmedBy;
		this.failureReason = note;
		this.settledAt = now;
	}

	/**
	 * Records how the investor says they paid a bank payment. They may correct the details until
	 * finance has settled the payment.
	 */
	public void submitDeposit(UUID companyBankAccountId, DepositMode mode, String reference, Instant now) {
		requireStatus(PaymentStatus.PENDING);
		if (method != PaymentMethod.BANK_TRANSFER) {
			throw new IllegalStateException("Only bank payments have deposit details");
		}
		this.companyBankAccountId = companyBankAccountId;
		this.depositMode = mode;
		this.depositReference = reference;
		this.depositSubmittedAt = now;
	}

	public boolean hasDepositDetails() {
		return depositSubmittedAt != null;
	}

	public void fail(String reason) {
		requireStatus(PaymentStatus.PENDING);
		this.status = PaymentStatus.FAILED;
		this.failureReason = reason;
	}

	public void cancel(String reason) {
		requireStatus(PaymentStatus.PENDING);
		this.status = PaymentStatus.CANCELLED;
		this.failureReason = reason;
	}

	public void markRefunded(UUID actorId, String reference, String reason, Instant now) {
		requireStatus(PaymentStatus.REFUND_REQUIRED);
		this.status = PaymentStatus.REFUNDED;
		this.refundedBy = actorId;
		this.refundReference = reference;
		this.refundReason = reason;
		this.refundedAt = now;
	}

	private void requireStatus(PaymentStatus expected) {
		if (status != expected) {
			throw new IllegalStateException("Payment is " + status + ", expected " + expected);
		}
	}

	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getUserId() {
		return userId;
	}

	public PaymentMethod getMethod() {
		return method;
	}

	public String getProvider() {
		return provider;
	}

	public String getProviderReference() {
		return providerReference;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public String getExternalReference() {
		return externalReference;
	}

	public UUID getConfirmedBy() {
		return confirmedBy;
	}

	public Instant getSettledAt() {
		return settledAt;
	}

	public Instant getRefundedAt() {
		return refundedAt;
	}

	public String getRefundReference() {
		return refundReference;
	}

	public String getRefundReason() {
		return refundReason;
	}

	public UUID getCompanyBankAccountId() {
		return companyBankAccountId;
	}

	public DepositMode getDepositMode() {
		return depositMode;
	}

	public String getDepositReference() {
		return depositReference;
	}

	public Instant getDepositSubmittedAt() {
		return depositSubmittedAt;
	}

}
