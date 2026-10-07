package com.sealease.backend.payment.entity;

public enum PaymentMethod {
	/** Investor transfers to the platform account; finance confirms receipt. */
	BANK_TRANSFER,
	/** Card payment through a gateway, which reports the outcome by signed webhook. */
	CARD
}
