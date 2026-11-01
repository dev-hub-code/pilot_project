package com.sealease.backend.investment.service;

import java.math.BigDecimal;

/** What holds for every investment. */
public final class PlanRules {

	/** The price of one unit of any plan, in the platform's currency. */
	public static final BigDecimal UNIT_PRICE = new BigDecimal("500");

	/** The largest amount in one order, in the platform's currency. */
	public static final BigDecimal MAX_INVESTMENT = new BigDecimal("1000000000");

	private PlanRules() {
	}

}
