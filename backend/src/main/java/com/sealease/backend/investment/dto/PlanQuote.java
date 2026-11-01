package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * What buying a number of units of a plan costs and pays, with exact decimal arithmetic.
 *
 * @param amount       units × the plan's unit price: what the investor pays
 * @param payouts      monthly payouts over the tenure
 * @param totalReturns the returns over the whole tenure
 * @param problems     why the purchase cannot be made as entered (empty when it can)
 */
public record PlanQuote(
		int units,
		MoneyResponse unitPrice,
		MoneyResponse amount,
		BigDecimal annualReturnPercent,
		MoneyResponse monthlyReturns,
		int payouts,
		MoneyResponse totalReturns,
		boolean valid,
		List<String> problems) {
}
