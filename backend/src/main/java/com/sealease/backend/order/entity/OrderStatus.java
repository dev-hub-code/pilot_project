package com.sealease.backend.order.entity;

/**
 * PENDING_PAYMENT (capacity reserved) → CONFIRMED (capacity committed, holdings created), or
 * EXPIRED / CANCELLED (capacity released). Every exit from PENDING_PAYMENT is final.
 */
public enum OrderStatus {
	PENDING_PAYMENT,
	CONFIRMED,
	EXPIRED,
	CANCELLED
}
