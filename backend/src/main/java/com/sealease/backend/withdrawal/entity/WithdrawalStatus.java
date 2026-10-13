package com.sealease.backend.withdrawal.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Bank: BATCHED (in the month's file) → PROCESSING (file paid by finance) → PAID, or FAILED. A failed payment is
 * held, with a support ticket, until finance resolves it: PAID (paid by hand) or RETURNED (back to the balance).
 * Cash at the office: APPROVED (made at the office) → PAID when handed over, or REJECTED.
 * Earlier investor requests went PENDING_APPROVAL → APPROVED first, or were CANCELLED by the investor.
 * While open, the amount is held in "withdrawals in transit"; RETURNED, REJECTED and CANCELLED give it back.
 */
public enum WithdrawalStatus {
	PENDING_APPROVAL,
	APPROVED,
	BATCHED,
	PROCESSING,
	PAID,
	FAILED,
	RETURNED,
	REJECTED,
	CANCELLED;

	public static final Set<WithdrawalStatus> OPEN = EnumSet.of(PENDING_APPROVAL, APPROVED, BATCHED, PROCESSING, FAILED);

	public boolean isOpen() {
		return OPEN.contains(this);
	}

}
