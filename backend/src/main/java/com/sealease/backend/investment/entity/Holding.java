package com.sealease.backend.investment.entity;

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

/**
 * A confirmed investment: an investor's share of one offering. It is the basis for rental income
 * distribution (Phase 6), so amount, ownership and accepted terms never change after creation.
 */
@Entity
@Table(name = "holdings")
public class Holding extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column(name = "order_id", nullable = false, updatable = false)
	private UUID orderId;

	@Column(name = "order_item_id", nullable = false, updatable = false)
	private UUID orderItemId;

	@Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "ownership_percent", nullable = false, updatable = false, precision = 9, scale = 4)
	private BigDecimal ownershipPercent;

	@Column(name = "terms_version", nullable = false, updatable = false, length = 20)
	private String termsVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private HoldingStatus status;

	@Column(name = "confirmed_at", nullable = false, updatable = false)
	private Instant confirmedAt;

	protected Holding() {
	}

	public Holding(UUID userId, UUID productId, UUID orderId, UUID orderItemId, Money amount,
			BigDecimal ownershipPercent, String termsVersion, Instant confirmedAt) {
		this.userId = userId;
		this.productId = productId;
		this.orderId = orderId;
		this.orderItemId = orderItemId;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.ownershipPercent = ownershipPercent;
		this.termsVersion = termsVersion;
		this.status = HoldingStatus.ACTIVE;
		this.confirmedAt = confirmedAt;
	}

	/** The lease of the offering has ended and all its rental has been distributed. */
	public void mature() {
		this.status = HoldingStatus.MATURED;
	}

	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getProductId() {
		return productId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getOrderItemId() {
		return orderItemId;
	}

	public BigDecimal getOwnershipPercent() {
		return ownershipPercent;
	}

	public String getTermsVersion() {
		return termsVersion;
	}

	public HoldingStatus getStatus() {
		return status;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

}
