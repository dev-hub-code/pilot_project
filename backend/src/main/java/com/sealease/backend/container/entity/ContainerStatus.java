package com.sealease.backend.container.entity;

public enum ContainerStatus {
	/** In inventory: can be reserved for an investor's order. */
	AVAILABLE,
	/** Held for an order awaiting payment. */
	RESERVED,
	/** Allocated to an investor and leased for the plan's tenure. */
	ON_LEASE,
	MAINTENANCE,
	/** Permanently out of service. */
	RETIRED;

	/** Statuses set by orders and leases, never by hand. */
	public boolean isAllocation() {
		return this == RESERVED || this == ON_LEASE;
	}
}
