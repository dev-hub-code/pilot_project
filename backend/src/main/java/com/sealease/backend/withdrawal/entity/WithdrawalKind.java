package com.sealease.backend.withdrawal.entity;

import com.sealease.backend.ledger.service.EarningsBalance;

/**
 * The two monthly bank withdrawal runs, each with its own file. Stored by name, so constants must never be
 * renamed once released.
 */
public enum WithdrawalKind {
	/** Rent on containers paid by bank. */
	RENT(EarningsBalance.RENT_BANK, "Rent"),
	/** Referral commission of investors paid by bank this year. */
	REFERRAL(EarningsBalance.REFERRAL, "Referral");

	private final EarningsBalance balance;
	private final String label;

	WithdrawalKind(EarningsBalance balance, String label) {
		this.balance = balance;
		this.label = label;
	}

	public EarningsBalance balance() {
		return balance;
	}

	/** As in the file name: "Rent", "Referral". */
	public String label() {
		return label;
	}

}
