package com.sealease.backend.order.entity;

import com.sealease.backend.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * What an order bought, with the plan terms the investor accepted - frozen when the order is placed.
 */
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

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	/** The units bought; null on orders placed before units. */
	@Column(name = "units")
	private Integer units;

	/** What one unit cost; null on orders placed before units. */
	@Column(name = "unit_price", precision = 19, scale = 4)
	private BigDecimal unitPrice;

	@Column(name = "monthly_rent_percent", nullable = false, precision = 12, scale = 8)
	private BigDecimal monthlyRentPercent;

	@Column(name = "monthly_capital_return_percent", nullable = false, precision = 9, scale = 4)
	private BigDecimal monthlyCapitalReturnPercent;

	@Column(name = "tenure_months", nullable = false)
	private int tenureMonths;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected OrderItem() {
	}

	public OrderItem(UUID orderId, UUID productId, String productCode, String productTitle, Money amount, int units,
			BigDecimal unitPrice, BigDecimal monthlyRentPercent,
			BigDecimal monthlyCapitalReturnPercent, int tenureMonths, Instant createdAt) {
		this.orderId = orderId;
		this.productId = productId;
		this.productCode = productCode;
		this.productTitle = productTitle;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.units = units;
		this.unitPrice = unitPrice;
		this.monthlyRentPercent = monthlyRentPercent;
		this.monthlyCapitalReturnPercent = monthlyCapitalReturnPercent;
		this.tenureMonths = tenureMonths;
		this.createdAt = createdAt;
	}

	/** The amount invested. */
	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
	}

	public Integer getUnits() {
		return units;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
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

	public BigDecimal getMonthlyRentPercent() {
		return monthlyRentPercent;
	}

	public BigDecimal getMonthlyCapitalReturnPercent() {
		return monthlyCapitalReturnPercent;
	}

	public int getTenureMonths() {
		return tenureMonths;
	}

}
