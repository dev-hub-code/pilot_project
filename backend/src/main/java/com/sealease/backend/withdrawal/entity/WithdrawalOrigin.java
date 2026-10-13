package com.sealease.backend.withdrawal.entity;

/** Who started a withdrawal. Stored by name, so constants must never be renamed once released. */
public enum WithdrawalOrigin {
	/** Requested by the investor; approved by finance. */
	INVESTOR,
	/** Made by the monthly withdrawal run for the investor's whole balance; approved by the schedule. */
	SCHEDULED,
	/** Made by finance at the office, in front of the investor, for cash handed over there. */
	OFFICE
}
