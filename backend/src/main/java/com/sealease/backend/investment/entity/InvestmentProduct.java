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
 * An investment offering backed by one container. Commercial terms are frozen once published;
 * capacity counters change only through {@link com.sealease.backend.investment.service.CapacityService}.
 */
@Entity
@Table(name = "investment_products")
public class InvestmentProduct extends BaseEntity {

	@Column(name = "code", nullable = false, length = 20, updatable = false)
	private String code;

	@Column(name = "container_id", nullable = false, updatable = false)
	private UUID containerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "investment_type", nullable = false, length = 10)
	private InvestmentType investmentType;

	@Column(name = "title", nullable = false, length = 140)
	private String title;

	@Column(name = "summary", nullable = false, length = 400)
	private String summary;

	@Column(name = "description", nullable = false, columnDefinition = "text")
	private String description;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal totalAmount;

	@Column(name = "minimum_investment", nullable = false, precision = 19, scale = 4)
	private BigDecimal minimumInvestment;

	@Column(name = "investment_increment", nullable = false, precision = 19, scale = 4)
	private BigDecimal investmentIncrement;

	@Column(name = "maximum_per_investor", precision = 19, scale = 4)
	private BigDecimal maximumPerInvestor;

	@Column(name = "committed_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal committedAmount = BigDecimal.ZERO;

	@Column(name = "reserved_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal reservedAmount = BigDecimal.ZERO;

	@Column(name = "expected_rental_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal expectedRentalAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "rental_frequency", nullable = false, length = 10)
	private RentalFrequency rentalFrequency;

	@Column(name = "duration_months", nullable = false)
	private int durationMonths;

	@Column(name = "lessee_name", length = 140)
	private String lesseeName;

	@Enumerated(EnumType.STRING)
	@Column(name = "risk_level", nullable = false, length = 10)
	private RiskLevel riskLevel;

	@Column(name = "risk_disclosure", nullable = false, columnDefinition = "text")
	private String riskDisclosure;

	@Column(name = "terms_and_conditions", nullable = false, columnDefinition = "text")
	private String termsAndConditions;

	@Column(name = "terms_version", nullable = false, length = 20)
	private String termsVersion;

	@Column(name = "offer_opens_at")
	private Instant offerOpensAt;

	@Column(name = "offer_closes_at")
	private Instant offerClosesAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ProductStatus status;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "published_by")
	private UUID publishedBy;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "cancellation_reason", length = 500)
	private String cancellationReason;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	protected InvestmentProduct() {
	}

	public InvestmentProduct(String code, UUID containerId, UUID createdBy) {
		this.code = code;
		this.containerId = containerId;
		this.createdBy = createdBy;
		this.status = ProductStatus.DRAFT;
	}

	public void defineTerms(ProductTerms t) {
		this.investmentType = t.investmentType();
		this.title = t.title();
		this.summary = t.summary();
		this.description = t.description();
		this.currency = t.currency().getCurrencyCode();
		this.totalAmount = t.totalAmount();
		this.minimumInvestment = t.minimumInvestment();
		this.investmentIncrement = t.investmentIncrement();
		this.maximumPerInvestor = t.maximumPerInvestor();
		this.expectedRentalAmount = t.expectedRentalAmount();
		this.rentalFrequency = t.rentalFrequency();
		this.durationMonths = t.durationMonths();
		this.lesseeName = t.lesseeName();
		this.riskLevel = t.riskLevel();
		this.riskDisclosure = t.riskDisclosure();
		this.termsAndConditions = t.termsAndConditions();
		this.termsVersion = t.termsVersion();
		this.offerOpensAt = t.offerOpensAt();
		this.offerClosesAt = t.offerClosesAt();
	}

	public void publish(UUID actorId, Instant now) {
		this.status = ProductStatus.OPEN;
		this.publishedAt = now;
		this.publishedBy = actorId;
	}

	public void cancel(String reason, Instant now) {
		this.status = ProductStatus.CANCELLED;
		this.cancellationReason = reason;
		this.cancelledAt = now;
	}

	/** Capacity counter changes; callers hold the row lock and record a movement. */
	public void applyReservation(BigDecimal amount) {
		this.reservedAmount = reservedAmount.add(amount);
	}

	public void applyRelease(BigDecimal amount) {
		this.reservedAmount = reservedAmount.subtract(amount);
	}

	public void applyCommit(BigDecimal amount) {
		this.reservedAmount = reservedAmount.subtract(amount);
		this.committedAmount = committedAmount.add(amount);
		if (committedAmount.compareTo(totalAmount) == 0 && status == ProductStatus.OPEN) {
			this.status = ProductStatus.FUNDED;
		}
	}

	public boolean isAcceptingInvestmentsAt(Instant now) {
		return status == ProductStatus.OPEN && (offerOpensAt == null || !now.isBefore(offerOpensAt))
				&& (offerClosesAt == null || now.isBefore(offerClosesAt));
	}

	public ProductTerms terms() {
		return new ProductTerms(investmentType, title, summary, description, currencyUnit(), totalAmount,
				minimumInvestment, investmentIncrement, maximumPerInvestor, expectedRentalAmount, rentalFrequency,
				durationMonths, lesseeName, riskLevel, riskDisclosure, termsAndConditions, termsVersion, offerOpensAt,
				offerClosesAt);
	}

	public Currency currencyUnit() {
		return Currency.getInstance(currency);
	}

	public Money total() {
		return Money.of(totalAmount, currencyUnit());
	}

	public Money committed() {
		return Money.of(committedAmount, currencyUnit());
	}

	public Money reserved() {
		return Money.of(reservedAmount, currencyUnit());
	}

	public Money available() {
		return total().minus(committed()).minus(reserved());
	}

	public String getCode() {
		return code;
	}

	public UUID getContainerId() {
		return containerId;
	}

	public InvestmentType getInvestmentType() {
		return investmentType;
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
