package com.sealease.backend.marketplace.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * What buying {@code containers} containers under a plan costs and pays, with exact decimal
 * arithmetic.
 *
 * @param payouts  number of monthly payouts (the tenure in months)
 * @param problems why the purchase cannot be made as entered (empty when it can)
 */
public record ReturnProjection(
		int containers,
		MoneyResponse amount,
		MoneyResponse monthlyRent,
		MoneyResponse monthlyCapitalReturn,
		MoneyResponse monthlyPayout,
		int payouts,
		MoneyResponse totalRent,
		MoneyResponse totalCapitalReturned,
		MoneyResponse totalPayout,
		boolean valid,
		List<String> problems) {
}
