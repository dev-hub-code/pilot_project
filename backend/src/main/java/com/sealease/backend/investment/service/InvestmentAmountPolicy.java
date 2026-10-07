package com.sealease.backend.investment.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductTerms;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates an investment amount against an offering's terms and current availability. Pure and
 * side-effect free: used for marketplace projections and enforced again when capacity is reserved.
 */
public final class InvestmentAmountPolicy {

	private InvestmentAmountPolicy() {
	}

	/**
	 * @param existingExposure what this investor already holds or has reserved in the offering
	 * @return human-readable violations; empty when the amount is acceptable
	 */
	public static List<String> violations(ProductTerms terms, Money amount, Money available, Money existingExposure) {
		List<String> problems = new ArrayList<>();
		if (!amount.currency().equals(terms.currency())) {
			problems.add("Amount must be in " + terms.currency().getCurrencyCode());
			return problems;
		}
		if (!amount.isPositive()) {
			problems.add("Amount must be positive");
			return problems;
		}
		int fractionDigits = Math.max(terms.currency().getDefaultFractionDigits(), 0);
		if (amount.amount().stripTrailingZeros().scale() > fractionDigits) {
			problems.add("Amount cannot have more than " + fractionDigits + " decimal places");
		}
		Money minimum = Money.of(terms.minimumInvestment(), terms.currency());
		Money increment = Money.of(terms.investmentIncrement(), terms.currency());

		if (terms.investmentType() == InvestmentType.HNI) {
			Money total = Money.of(terms.totalAmount(), terms.currency());
			if (amount.compareTo(total) != 0) {
				problems.add("A standalone container must be purchased in full (" + total.display() + ")");
			}
		}
		else {
			if (amount.isLessThan(minimum)) {
				problems.add("Minimum investment is " + minimum.display());
			}
			else if (amount.minus(minimum).amount().remainder(increment.amount()).signum() != 0) {
				problems.add("Amount must be " + minimum.display() + " plus multiples of " + increment.display());
			}
			if (terms.maximumPerInvestor() != null) {
				Money maximum = Money.of(terms.maximumPerInvestor(), terms.currency());
				if (existingExposure.plus(amount).isGreaterThan(maximum)) {
					problems.add("Maximum per investor is " + maximum.display() + "; you already hold or have reserved "
							+ existingExposure.display());
				}
			}
		}
		if (amount.isGreaterThan(available)) {
			problems.add(available.isZero() ? "This offering has no capacity left"
					: "Only " + available.display() + " is still available");
		}
		return problems;
	}

	/** Percentage of the asset an amount buys, 4 decimals. */
	public static BigDecimal ownershipPercent(ProductTerms terms, Money amount) {
		return amount.amount().multiply(BigDecimal.valueOf(100))
			.divide(terms.totalAmount(), 4, Money.ROUNDING);
	}

	/** The investor's share of one expected rental payment, net of the fee: rental × (1 − fee) × amount ÷ price. */
	public static Money rentalShare(ProductTerms terms, Money amount) {
		return Money.of(terms.expectedRentalAmount().multiply(terms.investorShareOfRental()).multiply(amount.amount())
			.divide(terms.totalAmount(), Money.SCALE, Money.ROUNDING), terms.currency());
	}

}
