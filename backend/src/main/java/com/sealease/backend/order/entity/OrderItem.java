package com.sealease.backend.order.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.container.entity.ContainerType;
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

/**
 * One container in an order, with the plan terms the investor accepted - frozen at checkout. The
 * container is reserved for the order and allocated to the investor once it is paid.
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

	@Enumerated(EnumType.STRING)
	@Column(name = "container_type", nullable = false, length = 30)
	private ContainerType containerType;

	@Column(name = "container_id", nullable = false)
	private UUID containerId;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "monthly_rent_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal monthlyRentPercent;

	@Column(name = "monthly_capital_return_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal monthlyCapitalReturnPercent;

	@Column(name = "tenure_months", nullable = false)
	private int tenureMonths;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected OrderItem() {
	}

	public OrderItem(UUID orderId, UUID productId, String productCode, String productTitle, ContainerType containerType,
			UUID containerId, Money amount, BigDecimal monthlyRentPercent, BigDecimal monthlyCapitalReturnPercent,
			int tenureMonths, Instant createdAt) {
		this.orderId = orderId;
		this.productId = productId;
		this.productCode = productCode;
		this.productTitle = productTitle;
		this.containerType = containerType;
		this.containerId = containerId;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.monthlyRentPercent = monthlyRentPercent;
		this.monthlyCapitalReturnPercent = monthlyCapitalReturnPercent;
		this.tenureMonths = tenureMonths;
		this.createdAt = createdAt;
	}

	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
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

	public ContainerType getContainerType() {
		return containerType;
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

}
