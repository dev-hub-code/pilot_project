package com.sealease.backend.investment.entity;

/** ACTIVE once confirmed; MATURED when the offering's lease has been paid out; CLOSED from later phases. */
public enum HoldingStatus {
	ACTIVE,
	MATURED,
	CLOSED
}
