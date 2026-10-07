package com.sealease.backend.investment.entity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;

/** The commercial terms of an offering, as investors see and accept them. */
public record ProductTerms(
		InvestmentType investmentType,
		String title,
		String summary,
		String description,
		Currency currency,
		BigDecimal totalAmount,
		BigDecimal minimumInvestment,
		BigDecimal investmentIncrement,
		BigDecimal maximumPerInvestor,
		BigDecimal expectedRentalAmount,
		RentalFrequency rentalFrequency,
		int durationMonths,
		BigDecimal managementFeePercent,
		String lesseeName,
		RiskLevel riskLevel,
		String riskDisclosure,
		String termsAndConditions,
		String termsVersion,
		Instant offerOpensAt,
		Instant offerClosesAt) {

	/**
	 * Annual rental yield to investors in percent, 2 decimals, net of the management fee:
	 * rental × (1 − fee) × periods per year ÷ price × 100.
	 */
	public BigDecimal expectedAnnualReturnPercent() {
		return expectedRentalAmount.multiply(investorShareOfRental())
			.multiply(BigDecimal.valueOf(rentalFrequency.periodsPerYear()))
			.multiply(BigDecimal.valueOf(100))
			.divide(totalAmount, MathContext.DECIMAL128)
			.setScale(2, RoundingMode.HALF_UP);
	}

	/** The part of each rental payment that goes to investors: 1 − fee ÷ 100. */
	public BigDecimal investorShareOfRental() {
		return BigDecimal.ONE.subtract(managementFeePercent.movePointLeft(2));
	}

}
