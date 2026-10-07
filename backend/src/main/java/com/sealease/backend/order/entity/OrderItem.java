package com.sealease.backend.order.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.RentalFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/** One offering in an order, with the terms the investor accepted - frozen at checkout. */
@Entity
@Immutable
@Table(name = "order_items")
public class OrderItem {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "product_code", nullable = false, length = 20)
	private String productCode;

	@Column(name = "product_title", nullable = false, length = 140)
	private String productTitle;

	@Enumerated(EnumType.STRING)
	@Column(name = "investment_type", nullable = false, length = 10)
	private InvestmentType investmentType;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "ownership_percent", nullable = false, precision = 9, scale = 4)
	private BigDecimal ownershipPercent;

	@Column(name = "terms_version", nullable = false, length = 20)
	private String termsVersion;

	@Column(name = "rental_per_payment", nullable = false, precision = 19, scale = 4)
	private BigDecimal rentalPerPayment;

	@Enumerated(EnumType.STRING)
	@Column(name = "rental_frequency", nullable = false, length = 10)
	private RentalFrequency rentalFrequency;

	@Column(name = "duration_months", nullable = false)
	private int durationMonths;

	@Column(name = "capacity_reference", nullable = false, length = 100)
	private String capacityReference;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected OrderItem() {
	}

	public OrderItem(UUID orderId, UUID productId, String productCode, String productTitle,
			InvestmentType investmentType, Money amount, BigDecimal ownershipPercent, String termsVersion,
			Money rentalPerPayment, RentalFrequency rentalFrequency, int durationMonths, Instant createdAt) {
		this.orderId = orderId;
		this.productId = productId;
		this.productCode = productCode;
		this.productTitle = productTitle;
		this.investmentType = investmentType;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.ownershipPercent = ownershipPercent;
		this.termsVersion = termsVersion;
		this.rentalPerPayment = rentalPerPayment.amount();
		this.rentalFrequency = rentalFrequency;
		this.durationMonths = durationMonths;
		this.capacityReference = capacityReference(orderId, productId);
		this.createdAt = createdAt;
	}

	/** The reservation key in the capacity ledger; unique because an order lists each offering once. */
	public static String capacityReference(UUID orderId, UUID productId) {
		return "order-" + orderId + ":" + productId;
	}

	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
	}

	public Money rentalPerPayment() {
		return Money.of(rentalPerPayment, Currency.getInstance(currency));
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getProductId() {
		return productId;
	}

	public String getProductCode() {
		return productCode;
	}

	public String getProductTitle() {
		return productTitle;
	}

	public InvestmentType getInvestmentType() {
		return investmentType;
	}

	public BigDecimal getOwnershipPercent() {
		return ownershipPercent;
	}

	public String getTermsVersion() {
		return termsVersion;
	}

	public RentalFrequency getRentalFrequency() {
		return rentalFrequency;
	}

	public int getDurationMonths() {
		return durationMonths;
	}

	public String getCapacityReference() {
		return capacityReference;
	}

}
