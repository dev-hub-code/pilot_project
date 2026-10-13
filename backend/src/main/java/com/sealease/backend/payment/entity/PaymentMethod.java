package com.sealease.backend.payment.entity;

public enum PaymentMethod {
	/**
	 * Investor pays into a company bank account (online transfer, cheque or cash deposit) and submits
	 * the details; finance confirms receipt.
	 */
	BANK_TRANSFER,
	/** Investor pays in cash at the company's office; finance confirms it with the receipt number. */
	OFFICE_CASH,
	/** Card payment through a gateway. No longer accepted; kept so earlier card payments stay readable. */
	CARD
}
