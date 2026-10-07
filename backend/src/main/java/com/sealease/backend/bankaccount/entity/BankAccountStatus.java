package com.sealease.backend.bankaccount.entity;

public enum BankAccountStatus {
	PENDING_VERIFICATION,
	VERIFIED,
	REJECTED,
	/** Removed by the owner; kept for payout history and fraud analysis. */
	REMOVED
}
