package com.sealease.backend.ledger.service;

/** Stored by name, so constants must never be renamed once released. */
public enum TransactionType {
	RENTAL_DISTRIBUTION,
	ADJUSTMENT,
	REFERRAL_COMMISSION,
	WITHDRAWAL_RESERVE,
	WITHDRAWAL_RELEASE,
	WITHDRAWAL_PAYOUT
}
