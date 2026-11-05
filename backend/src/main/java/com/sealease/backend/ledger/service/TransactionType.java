package com.sealease.backend.ledger.service;

/** Stored by name, so constants must never be renamed once released. */
public enum TransactionType {
	INVESTOR_PAYOUT,
	ADJUSTMENT,
	REFERRAL_COMMISSION,
	WITHDRAWAL_RESERVE,
	WITHDRAWAL_RELEASE,
	WITHDRAWAL_PAYOUT,
	/** Money received for an investment (reference: the payment id). */
	DEPOSIT_RECEIVED,
	/** Money received for an investment that could not take it, paid back (reference: the payment id). */
	DEPOSIT_REFUNDED,
	/** An investor's earnings spent on an investment, paid from their available balance (reference: the payment id). */
	REINVESTMENT
}
