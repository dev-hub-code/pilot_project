package com.sealease.backend.withdrawal.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * PENDING_APPROVAL → APPROVED → BATCHED → PROCESSING (file sent to the bank) → PAID or FAILED;
 * REJECTED by finance or CANCELLED by the investor before approval. While open, the amount is held
 * in "withdrawals in transit"; REJECTED, CANCELLED and FAILED return it to the investor.
 */
public enum WithdrawalStatus {
	PENDING_APPROVAL,
	APPROVED,
	BATCHED,
	PROCESSING,
	PAID,
	FAILED,
	REJECTED,
	CANCELLED;

	public static final Set<WithdrawalStatus> OPEN = EnumSet.of(PENDING_APPROVAL, APPROVED, BATCHED, PROCESSING);

	public boolean isOpen() {
		return OPEN.contains(this);
	}

}
