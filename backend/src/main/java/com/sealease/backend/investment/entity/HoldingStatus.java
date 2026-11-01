package com.sealease.backend.investment.entity;

public enum HoldingStatus {
	/** Paid for; the term and returns start when the investor accepts the investment agreement. */
	AWAITING_ACCEPTANCE,
	/** The term is running and returns are being credited. */
	ACTIVE,
	/** Every payout of the term has been paid. */
	MATURED
}
