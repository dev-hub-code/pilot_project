package com.sealease.backend.investment.entity;

import com.sealease.backend.user.entity.InvestorType;

public enum InvestmentType {

	/** A shared container, co-owned by many investors in proportion to their contribution. */
	RETAIL,
	/** A standalone container, owned 100% by a single high-net-worth investor. */
	HNI;

	/** Retail offerings are open to every investor; standalone offerings only to HNI investors. */
	public boolean isOpenTo(InvestorType investorType) {
		return this == RETAIL || investorType == InvestorType.HNI;
	}

}
