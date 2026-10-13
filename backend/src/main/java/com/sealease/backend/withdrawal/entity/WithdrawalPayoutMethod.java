package com.sealease.backend.withdrawal.entity;

/** How a withdrawal reaches the investor. Stored by name, so constants must never be renamed once released. */
public enum WithdrawalPayoutMethod {
	/** Paid into the investor's verified bank account, in a bank batch. */
	BANK_TRANSFER,
	/** Collected by the investor in cash at the company's office; finance records the hand-over. */
	OFFICE_CASH
}
