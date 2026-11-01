package com.sealease.backend.investment.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import com.sealease.backend.referral.dto.ReferralScheme;
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
 * An investment plan: one tenure, offered to one investor type, who buy units of it and are paid a monthly return for
 * the tenure. Terms are frozen once published.
 */
@Entity
@Table(name = "investment_products")
public class InvestmentProduct extends BaseEntity {

	@Column(name = "code", nullable = false, length = 20, updatable = false)
	private String code;

	/** The investor type this plan is offered to. */
	@Enumerated(EnumType.STRING)
	@Column(name = "audience", nullable = false, length = 20)
	private ReferralScheme audience;

	@Column(name = "title", nullable = false, length = 140)
	private String title;

	@Column(name = "summary", nullable = false, length = 400)
	private String summary;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "monthly_capital_return_percent", nullable = false, precision = 9, scale = 4)
	private BigDecimal monthlyCapitalReturnPercent;

	@Column(name = "tenure_years", nullable = false)
	private int tenureYears;

	@Column(name = "annual_return_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal annualReturnPercent;

	@Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
	private BigDecimal unitPrice;

	@Column(name = "min_units", nullable = false)
	private int minUnits;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ProductStatus status;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "published_by")
	private UUID publishedBy;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "cancellation_reason", length = 500)
	private String cancellationReason;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	protected InvestmentProduct() {
	}

	public InvestmentProduct(String code, UUID createdBy) {
		this.code = code;
		this.createdBy = createdBy;
		this.status = ProductStatus.DRAFT;
	}

	public void defineTerms(ProductTerms t) {
		this.title = t.title();
		this.summary = t.summary();
		this.currency = t.currency().getCurrencyCode();
		this.monthlyCapitalReturnPercent = t.monthlyCapitalReturnPercent();
		this.tenureYears = t.tenureYears();
		this.annualReturnPercent = t.annualReturnPercent();
		this.unitPrice = t.unitPrice();
		this.minUnits = t.minUnits();
	}

	public void publish(UUID actorId, Instant now) {
		this.status = ProductStatus.OPEN;
		this.publishedAt = now;
		this.publishedBy = actorId;
	}

	/** No more investments are taken under the plan; those made keep their term and payouts. */
	public void close(Instant now) {
		this.status = ProductStatus.CLOSED;
		this.closedAt = now;
	}

	/** Takes investments under the plan again, on the terms it was published with. */
	public void reopen() {
		this.status = ProductStatus.OPEN;
		this.closedAt = null;
	}

	public void cancel(String reason, Instant now) {
		this.status = ProductStatus.CANCELLED;
		this.cancellationReason = reason;
		this.cancelledAt = now;
	}

	public boolean isAcceptingInvestments() {
		return status == ProductStatus.OPEN;
	}

	public ProductTerms terms() {
		return new ProductTerms(title, summary, currencyUnit(), monthlyCapitalReturnPercent, tenureYears,
				annualReturnPercent, unitPrice, minUnits);
	}

	public Currency currencyUnit() {
		return Currency.getInstance(currency);
	}

	public String getCode() {
		return code;
	}

	public ReferralScheme getAudience() {
		return audience;
	}

	public void setAudience(ReferralScheme audience) {
		this.audience = audience;
	}

	public ProductStatus getStatus() {
		return status;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public UUID getPublishedBy() {
		return publishedBy;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public String getCancellationReason() {
		return cancellationReason;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

}
