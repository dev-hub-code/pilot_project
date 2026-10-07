package com.sealease.backend.payment.entity;

public enum PaymentMethod {
	/**
	 * Investor pays into a company bank account (online transfer, cheque or cash deposit) and submits
	 * the details; finance confirms receipt.
	 */
	BANK_TRANSFER,
	/** Card payment through a gateway. No longer accepted; kept so earlier card payments stay readable. */
	CARD
}
