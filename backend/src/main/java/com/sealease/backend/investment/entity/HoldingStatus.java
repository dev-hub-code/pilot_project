package com.sealease.backend.investment.entity;

/** ACTIVE once confirmed; MATURED/CLOSED are driven by the leasing lifecycle in later phases. */
public enum HoldingStatus {
	ACTIVE,
	MATURED,
	CLOSED
}
