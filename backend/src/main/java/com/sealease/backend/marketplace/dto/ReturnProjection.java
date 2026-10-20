package com.sealease.backend.marketplace.dto;

import java.math.BigDecimal;
import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * What buying {@code containers} containers under a plan costs and pays, with exact decimal
 * arithmetic.
 *
 * @param tenureYears the lease the figures are for, between {@code minTenureYears} and {@code maxTenureYears}
 * @param amount      the price of the containers: what the investor pays
 * @param monthlyRentPercent the rent rate for an order of this amount (rent by order value)
 * @param payouts     number of monthly payouts (the lease in months)
 * @param totalRent   the rent over the whole lease (plans pay rent only)
 * @param problems why the purchase cannot be made as entered (empty when it can)
 */
public record ReturnProjection(
		int containers,
		int tenureYears,
		int minTenureYears,
		int maxTenureYears,
		MoneyResponse amount,
		BigDecimal monthlyRentPercent,
		MoneyResponse monthlyRent,
		int payouts,
		MoneyResponse totalRent,
		boolean valid,
		List<String> problems) {
}
