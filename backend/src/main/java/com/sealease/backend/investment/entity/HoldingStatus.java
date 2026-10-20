package com.sealease.backend.investment.entity;

public enum HoldingStatus {
	/** Paid for; finance has not assigned a container yet. No lease and no payouts until it does. */
	AWAITING_CONTAINER,
	/**
	 * Finance assigned the container (it is the investor's) and issued the lease agreement; the lease and rent start
	 * when the investor accepts it.
	 */
	AWAITING_ACCEPTANCE,
	/** The container is on lease and payouts are running. */
	ACTIVE,
	/** Every payout of the tenure has been paid; the container went back to inventory. */
	MATURED
}
