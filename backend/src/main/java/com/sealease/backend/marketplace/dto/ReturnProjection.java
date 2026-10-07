package com.sealease.backend.marketplace.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Indicative figures for a proposed amount, computed with exact decimal arithmetic. Expected
 * income is not guaranteed: it depends on the lessee paying rent as forecast.
 *
 * @param paymentsOverTerm number of rental payments within the offering's duration
 * @param problems         why the amount cannot be invested as entered (empty when valid)
 */
public record ReturnProjection(
		MoneyResponse amount,
		BigDecimal ownershipPercent,
		MoneyResponse rentalPerPayment,
		MoneyResponse expectedAnnualIncome,
		int paymentsOverTerm,
		MoneyResponse expectedIncomeOverTerm,
		boolean valid,
		List<String> problems) {
}
