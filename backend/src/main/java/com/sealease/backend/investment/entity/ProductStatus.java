package com.sealease.backend.investment.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Plan lifecycle: DRAFT → OPEN (investors buy containers) → CLOSED (no more sales), or CANCELLED
 * while still a draft. Containers already sold keep their lease and payout schedule either way.
 */
public enum ProductStatus {
	DRAFT,
	OPEN,
	CLOSED,
	CANCELLED;

	/** Statuses investors can see in the marketplace. */
	public static final Set<ProductStatus> LISTED = EnumSet.of(OPEN, CLOSED);

	public boolean isListed() {
		return LISTED.contains(this);
	}

}
