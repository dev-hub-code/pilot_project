package com.sealease.backend.investment.entity;

public enum CapacityMovementType {
	/** Capacity held for an investor (cart item / order awaiting payment). */
	RESERVE,
	/** A reservation returned to the pool (expired, cancelled, payment failed). */
	RELEASE,
	/** A reservation converted into a confirmed investment. */
	COMMIT
}
