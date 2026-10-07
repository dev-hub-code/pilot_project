package com.sealease.backend.investment.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Offering lifecycle: DRAFT → OPEN → FUNDED → ACTIVE → MATURED → CLOSED, or CANCELLED before any
 * capacity is taken. ACTIVE/MATURED/CLOSED are driven by the leasing lifecycle in later phases.
 */
public enum ProductStatus {
	DRAFT,
	OPEN,
	FUNDED,
	ACTIVE,
	MATURED,
	CLOSED,
	CANCELLED;

	/** Statuses investors can see in the marketplace. */
	public static final Set<ProductStatus> LISTED = EnumSet.of(OPEN, FUNDED, ACTIVE, MATURED);

	public boolean isListed() {
		return LISTED.contains(this);
	}

}
