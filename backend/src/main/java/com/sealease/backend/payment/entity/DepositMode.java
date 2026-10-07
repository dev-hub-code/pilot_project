package com.sealease.backend.payment.entity;

/** How an investor paid into a company bank account, and so what their reference is. */
public enum DepositMode {

	/** NEFT, RTGS, IMPS or UPI: the transaction id (UTR). */
	ONLINE,
	/** A cheque: its number. */
	CHEQUE,
	/** Cash paid in at a branch: the deposit receipt number. */
	CASH_DEPOSIT

}
