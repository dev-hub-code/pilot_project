package com.sealease.backend.cart.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/** An intended investment. Holds no capacity; everything is re-validated at checkout. */
@Entity
@Table(name = "cart_items")
public class CartItem extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	protected CartItem() {
	}

	public CartItem(UUID userId, UUID productId, Money amount) {
		this.userId = userId;
		this.productId = productId;
		this.currency = amount.currency().getCurrencyCode();
		changeAmount(amount);
	}

	public void changeAmount(Money amount) {
		if (!amount.currency().getCurrencyCode().equals(currency)) {
			throw new IllegalArgumentException("Cart item currency cannot change");
		}
		this.amount = amount.amount();
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

	public String getCurrency() {
		return currency;
	}

}
