package com.sealease.backend.investment.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import com.sealease.backend.container.entity.ContainerType;
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
 * An investment plan: investors buy whole containers of one type at a fixed price per container and
 * are paid monthly rent plus a return of capital for the tenure. Terms are frozen once published.
 */
@Entity
@Table(name = "investment_products")
public class InvestmentProduct extends BaseEntity {

	@Column(name = "code", nullable = false, length = 20, updatable = false)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(name = "container_type", nullable = false, length = 30)
	private ContainerType containerType;

	@Column(name = "title", nullable = false, length = 140)
	private String title;

	@Column(name = "summary", nullable = false, length = 400)
	private String summary;

	@Column(name = "description", nullable = false, columnDefinition = "text")
	private String description;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "price", nullable = false, precision = 19, scale = 4)
	private BigDecimal price;

	@Column(name = "monthly_rent_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal monthlyRentPercent;

	@Column(name = "monthly_capital_return_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal monthlyCapitalReturnPercent;

	@Column(name = "tenure_months", nullable = false)
	private int tenureMonths;

	@Column(name = "risk_disclosure", nullable = false, columnDefinition = "text")
	private String riskDisclosure;

	@Column(name = "terms_and_conditions", nullable = false, columnDefinition = "text")
	private String termsAndConditions;

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
		this.containerType = t.containerType();
		this.title = t.title();
		this.summary = t.summary();
		this.description = t.description();
		this.currency = t.currency().getCurrencyCode();
		this.price = t.price();
		this.monthlyRentPercent = t.monthlyRentPercent();
		this.monthlyCapitalReturnPercent = t.monthlyCapitalReturnPercent();
		this.tenureMonths = t.tenureMonths();
		this.riskDisclosure = t.riskDisclosure();
		this.termsAndConditions = t.termsAndConditions();
	}

	public void publish(UUID actorId, Instant now) {
		this.status = ProductStatus.OPEN;
		this.publishedAt = now;
		this.publishedBy = actorId;
	}

	/** No more containers are sold under the plan; those sold keep their lease and payouts. */
	public void close(Instant now) {
		this.status = ProductStatus.CLOSED;
		this.closedAt = now;
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
		return new ProductTerms(containerType, title, summary, description, currencyUnit(), price, monthlyRentPercent,
				monthlyCapitalReturnPercent, tenureMonths, riskDisclosure, termsAndConditions);
	}

	public Currency currencyUnit() {
		return Currency.getInstance(currency);
	}

	public String getCode() {
		return code;
	}

	public ContainerType getContainerType() {
		return containerType;
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
