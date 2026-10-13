package com.sealease.backend.investment.entity;

public enum HoldingStatus {
	/** Paid for; finance has not assigned a container yet. No lease and no payouts until it does. */
	AWAITING_CONTAINER,
	/** The container is on lease and payouts are running. */
	ACTIVE,
	/** Every payout of the tenure has been paid; the container went back to inventory. */
	MATURED
}
