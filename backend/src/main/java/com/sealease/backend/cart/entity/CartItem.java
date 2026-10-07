package com.sealease.backend.cart.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** An intended purchase of containers under a plan. Reserves nothing; everything is re-validated at checkout. */
@Entity
@Table(name = "cart_items")
public class CartItem extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected CartItem() {
	}

	public CartItem(UUID userId, UUID productId, int quantity) {
		this.userId = userId;
		this.productId = productId;
		this.quantity = quantity;
	}

	public void changeQuantity(int quantity) {
		this.quantity = quantity;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getProductId() {
		return productId;
	}

	public int getQuantity() {
		return quantity;
	}

}
