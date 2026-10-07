package com.sealease.backend.investment.entity;

public enum HoldingStatus {
	/** The container is on lease and payouts are running. */
	ACTIVE,
	/** Every payout of the tenure has been paid; the container went back to inventory. */
	MATURED
}
