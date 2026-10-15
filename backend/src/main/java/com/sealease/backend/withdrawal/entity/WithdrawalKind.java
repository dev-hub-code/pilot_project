package com.sealease.backend.withdrawal.entity;

import com.sealease.backend.ledger.service.EarningsBalance;

import java.util.List;

/**
 * What a withdrawal run (and its batches) withdraws: rent or referral commission, generated separately. Stored by
 * name, so constants must never be renamed once released. (Before withdrawal runs, the two monthly bank runs.)
 */
public enum WithdrawalKind {
	/** Rent: on containers paid by bank (bank sheet) and paid in cash at the office (cash sheet). */
	RENT(EarningsBalance.RENT_BANK, "Rent", List.of(EarningsBalance.RENT_BANK, EarningsBalance.RENT_CASH)),
	/** Referral commission: by bank or in cash, as each investor chose for the year. */
	REFERRAL(EarningsBalance.REFERRAL, "Referral", List.of(EarningsBalance.REFERRAL));

	private final EarningsBalance balance;
	private final String label;
	private final List<EarningsBalance> balances;

	WithdrawalKind(EarningsBalance balance, String label, List<EarningsBalance> balances) {
		this.balance = balance;
		this.label = label;
		this.balances = balances;
	}

	/** The balances a withdrawal run of this kind withdraws. */
	public List<EarningsBalance> balances() {
		return balances;
	}

	public EarningsBalance balance() {
		return balance;
	}

	/** As in the file name: "Rent", "Referral". */
	public String label() {
		return label;
	}

}
