package com.sealease.backend.order.entity;

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

/** An investor's checkout: one or more offerings, paid for in one payment. */
@Entity
@Table(name = "orders")
public class InvestmentOrder extends BaseEntity {

	@Column(name = "order_number", nullable = false, updatable = false, length = 20)
	private String orderNumber;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OrderStatus status;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "total_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal totalAmount;

	@Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, updatable = false, length = 64)
	private String requestHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "close_reason", length = 500)
	private String closeReason;

	protected InvestmentOrder() {
	}

	public InvestmentOrder(String orderNumber, UUID userId, Money total, String idempotencyKey, String requestHash,
			Instant expiresAt) {
		this.orderNumber = orderNumber;
		this.userId = userId;
		this.status = OrderStatus.PENDING_PAYMENT;
		this.currency = total.currency().getCurrencyCode();
		this.totalAmount = total.amount();
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.expiresAt = expiresAt;
	}

	public void confirm(Instant now) {
		requirePending();
		this.status = OrderStatus.CONFIRMED;
		this.confirmedAt = now;
	}

	public void close(OrderStatus outcome, String reason, Instant now) {
		requirePending();
		if (outcome != OrderStatus.EXPIRED && outcome != OrderStatus.CANCELLED) {
			throw new IllegalArgumentException("Not a closing status: " + outcome);
		}
		this.status = outcome;
		this.closeReason = reason;
		this.closedAt = now;
	}

	/** Keeps the reservation until {@code until} (never shortens it), while a payment is being verified. */
	public void holdUntil(Instant until) {
		requirePending();
		if (until.isAfter(expiresAt)) {
			this.expiresAt = until;
		}
	}

	public boolean isPendingPayment() {
		return status == OrderStatus.PENDING_PAYMENT;
	}

	private void requirePending() {
		if (!isPendingPayment()) {
			throw new IllegalStateException("Order " + orderNumber + " is already " + status);
		}
	}

	public Money total() {
		return Money.of(totalAmount, Currency.getInstance(currency));
	}

	public String getOrderNumber() {
		return orderNumber;
	}

	public UUID getUserId() {
		return userId;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public String getCloseReason() {
		return closeReason;
	}

}
