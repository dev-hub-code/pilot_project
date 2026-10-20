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

	/** Null while awaiting a container. */
	@Column(name = "container_id")
	private UUID containerId;

	@Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "monthly_rent_percent", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal monthlyRentPercent;

	@Column(name = "monthly_capital_return_percent", nullable = false, updatable = false, precision = 9, scale = 4)
	private BigDecimal monthlyCapitalReturnPercent;

	@Column(name = "tenure_months", nullable = false, updatable = false)
	private int tenureMonths;

	/** The lease starts when the container is assigned; both null until then. */
	@Column(name = "lease_starts_on")
	private LocalDate leaseStartsOn;

	@Column(name = "lease_ends_on")
	private LocalDate leaseEndsOn;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private HoldingStatus status;

	@Column(name = "confirmed_at", nullable = false, updatable = false)
	private Instant confirmedAt;

	/** The order was paid in cash at the office: the rent goes to the investor's cash balance, collected there. */
	@Column(name = "paid_at_office", nullable = false, updatable = false)
	private boolean paidAtOffice;

	@Column(name = "matured_at")
	private Instant maturedAt;

	@Column(name = "assigned_at")
	private Instant assignedAt;

	@Column(name = "assigned_by")
	private UUID assignedBy;

	/** The finance member who took the task of assigning the container; only they may assign it. */
	@Column(name = "task_assignee_id")
	private UUID taskAssigneeId;

	@Column(name = "task_assigned_at")
	private Instant taskAssignedAt;

	protected Holding() {
	}

	public Holding(UUID userId, UUID productId, UUID orderId, UUID orderItemId, UUID containerId, Money amount,
			BigDecimal monthlyRentPercent, BigDecimal monthlyCapitalReturnPercent, int tenureMonths,
			LocalDate leaseStartsOn, Instant confirmedAt, boolean paidAtOffice) {
		this.userId = userId;
		this.paidAtOffice = paidAtOffice;
		this.productId = productId;
		this.orderId = orderId;
		this.orderItemId = orderItemId;
		this.containerId = containerId;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.monthlyRentPercent = monthlyRentPercent;
		this.monthlyCapitalReturnPercent = monthlyCapitalReturnPercent;
		this.tenureMonths = tenureMonths;
		this.confirmedAt = confirmedAt;
		if (containerId == null) {
			this.status = HoldingStatus.AWAITING_CONTAINER;
		}
		else {
			this.leaseStartsOn = leaseStartsOn;
			this.leaseEndsOn = leaseStartsOn.plusMonths(tenureMonths);
			this.status = HoldingStatus.ACTIVE;
		}
	}

	/** Finance assigned the container; the lease starts once the investor accepts the lease agreement. */
	public void assignContainer(UUID containerId, UUID assignedBy, Instant now) {
		if (status != HoldingStatus.AWAITING_CONTAINER) {
			throw new IllegalStateException("Holding " + getId() + " is " + status + ", not awaiting a container");
		}
		this.containerId = containerId;
		this.status = HoldingStatus.AWAITING_ACCEPTANCE;
		this.assignedBy = assignedBy;
		this.assignedAt = now;
	}

	/** The investor accepted the lease agreement: the lease runs from {@code leaseStartsOn} for the tenure. */
	public void startLease(LocalDate leaseStartsOn) {
		if (status != HoldingStatus.AWAITING_ACCEPTANCE) {
			throw new IllegalStateException("Holding " + getId() + " is " + status + ", not awaiting acceptance");
		}
		this.leaseStartsOn = leaseStartsOn;
		this.leaseEndsOn = leaseStartsOn.plusMonths(tenureMonths);
		this.status = HoldingStatus.ACTIVE;
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

	/** Capital returned every month of the tenure: only under terms sold before capital returns ended. */
	public Money monthlyCapitalReturn() {
		return ProductTerms.returnsCapital(monthlyCapitalReturnPercent)
				? ProductTerms.capitalPerMonth(amount(), tenureMonths) : Money.zero(amount().currency());
	}

	/** The capital paid back over the whole tenure: the price under earlier terms, otherwise nothing. */
	public Money capitalReturned() {
		return ProductTerms.capitalReturned(amount(), monthlyCapitalReturnPercent);
	}

	/** A finance member takes the task of assigning this investment's container. */
	public void takeTask(UUID assignee, Instant now) {
		this.taskAssigneeId = assignee;
		this.taskAssignedAt = now;
	}

	/** The task goes back to the queue, for another finance member to take. */
	public void releaseTask() {
		this.taskAssigneeId = null;
		this.taskAssignedAt = null;
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

	public UUID getProductId() {
		return productId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getOrderItemId() {
		return orderItemId;
	}

	public Instant getAssignedAt() {
		return assignedAt;
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

	public boolean isPaidAtOffice() {
		return paidAtOffice;
	}

	public Instant getMaturedAt() {
		return maturedAt;
	}

}
