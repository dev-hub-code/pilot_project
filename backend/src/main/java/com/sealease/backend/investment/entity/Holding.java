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
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

/**
 * A confirmed investment: one container allocated to one investor and leased for the plan's tenure
 * from the day payment was confirmed. Amount and accepted terms never change after creation.
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

	@Column(name = "container_id", nullable = false, updatable = false)
	private UUID containerId;

	@Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "monthly_rent_percent", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal monthlyRentPercent;

	@Column(name = "monthly_capital_return_percent", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal monthlyCapitalReturnPercent;

	@Column(name = "tenure_months", nullable = false, updatable = false)
	private int tenureMonths;

	@Column(name = "lease_starts_on", nullable = false, updatable = false)
	private LocalDate leaseStartsOn;

	@Column(name = "lease_ends_on", nullable = false, updatable = false)
	private LocalDate leaseEndsOn;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private HoldingStatus status;

	@Column(name = "confirmed_at", nullable = false, updatable = false)
	private Instant confirmedAt;

	@Column(name = "matured_at")
	private Instant maturedAt;

	protected Holding() {
	}

	public Holding(UUID userId, UUID productId, UUID orderId, UUID orderItemId, UUID containerId, Money amount,
			BigDecimal monthlyRentPercent, BigDecimal monthlyCapitalReturnPercent, int tenureMonths,
			LocalDate leaseStartsOn, Instant confirmedAt) {
		this.userId = userId;
		this.productId = productId;
		this.orderId = orderId;
		this.orderItemId = orderItemId;
		this.containerId = containerId;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.monthlyRentPercent = monthlyRentPercent;
		this.monthlyCapitalReturnPercent = monthlyCapitalReturnPercent;
		this.tenureMonths = tenureMonths;
		this.leaseStartsOn = leaseStartsOn;
		this.leaseEndsOn = leaseStartsOn.plusMonths(tenureMonths);
		this.status = HoldingStatus.ACTIVE;
		this.confirmedAt = confirmedAt;
	}

	/** The last payout of the tenure has been paid. */
	public void mature(Instant now) {
		this.status = HoldingStatus.MATURED;
		this.maturedAt = now;
	}

	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
	}

	/** Rent paid every month of the tenure. */
	public Money monthlyRent() {
		return ProductTerms.percentOf(amount(), monthlyRentPercent);
	}

	/** Capital returned every month of the tenure (the last month absorbs the rounding). */
	public Money monthlyCapitalReturn() {
		return ProductTerms.capitalPerMonth(amount(), tenureMonths);
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

	public UUID getContainerId() {
		return containerId;
	}

	public BigDecimal getMonthlyRentPercent() {
		return monthlyRentPercent;
	}

	public BigDecimal getMonthlyCapitalReturnPercent() {
		return monthlyCapitalReturnPercent;
	}

	public int getTenureMonths() {
		return tenureMonths;
	}

	public LocalDate getLeaseStartsOn() {
		return leaseStartsOn;
	}

	public LocalDate getLeaseEndsOn() {
		return leaseEndsOn;
	}

	public HoldingStatus getStatus() {
		return status;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public Instant getMaturedAt() {
		return maturedAt;
	}

}
