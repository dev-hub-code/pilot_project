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
		String lesseeName,
		RiskLevel riskLevel,
		String riskDisclosure,
		String termsAndConditions,
		String termsVersion,
		Instant offerOpensAt,
		Instant offerClosesAt) {

	/** Gross annual rental yield in percent, 2 decimals: rental × periods per year ÷ price × 100. */
	public BigDecimal expectedAnnualReturnPercent() {
		return expectedRentalAmount.multiply(BigDecimal.valueOf(rentalFrequency.periodsPerYear()))
			.multiply(BigDecimal.valueOf(100))
			.divide(totalAmount, MathContext.DECIMAL128)
			.setScale(2, RoundingMode.HALF_UP);
	}

}
