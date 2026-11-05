package com.sealease.backend.payment.entity;

public enum PaymentMethod {
	/**
	 * Investor pays into a company bank account (online transfer or cheque) and submits
	 * the details; finance confirms receipt.
	 */
	BANK_TRANSFER,
	/** Investor pays in cash at the company's office; finance confirms it with the receipt number. */
	OFFICE_CASH,
	/** Card payment through a gateway. No longer accepted; kept so earlier card payments stay readable. */
	CARD,
	/**
	 * Online investors: the gateway's checkout (UPI or net banking). Confirmed automatically once the gateway reports the
	 * money taken.
	 */
	GATEWAY,
	/**
	 * Online investors: a virtual bank account (and UPI ID) opened for this payment, paid by NEFT, RTGS, IMPS or UPI.
	 * Confirmed automatically when the money lands.
	 */
	VIRTUAL_ACCOUNT,
	/**
	 * Online investors: paid from their available balance (the earnings in their wallet), confirmed at once. The ledger
	 * moves the amount from their earnings to their invested capital in the same transaction.
	 */
	WALLET
}
