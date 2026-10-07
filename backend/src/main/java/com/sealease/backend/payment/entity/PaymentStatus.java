package com.sealease.backend.payment.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * PENDING → SUCCEEDED (order confirmed) or FAILED / CANCELLED. Money that arrives for an order
 * that can no longer be confirmed (expired, cancelled, already paid) becomes REFUND_REQUIRED and,
 * once finance has returned it, REFUNDED.
 */
public enum PaymentStatus {
	PENDING,
	SUCCEEDED,
	FAILED,
	CANCELLED,
	REFUND_REQUIRED,
	REFUNDED;

	/** Statuses in which the platform holds (or held) the investor's money. */
	public static final Set<PaymentStatus> SETTLED = EnumSet.of(SUCCEEDED, REFUND_REQUIRED, REFUNDED);

	public boolean isSettled() {
		return SETTLED.contains(this);
	}

}
