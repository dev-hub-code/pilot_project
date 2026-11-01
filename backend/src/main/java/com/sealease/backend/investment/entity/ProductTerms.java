package com.sealease.backend.investment.entity;

import com.sealease.backend.common.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;

/**
 * The commercial terms of a plan, as investors see and accept them. A plan is one tenure for one investor type: the
 * investor buys whole units at {@code unitPrice}, at least {@code minUnits}, and earns {@code annualReturnPercent} of what they
 * invested a year, paid monthly for the tenure. The rate and price are fixed on the order. The investment is not
 * paid back: plans carry a {@code monthlyCapitalReturnPercent} of zero.
 *
 * <p>Plans sold earlier returned the whole amount over the tenure as well. Holdings bought under those terms keep them,
 * so the percentage recorded with a purchase decides whether it returns capital ({@link #returnsCapital}).
 */
public record ProductTerms(
		String title,
		String summary,
		Currency currency,
		BigDecimal monthlyCapitalReturnPercent,
		int tenureYears,
		BigDecimal annualReturnPercent,
		BigDecimal unitPrice,
		int minUnits) {

	/** The share of an annual rate paid each month, as orders and holdings keep it. */
	public static BigDecimal monthlyPercentOf(BigDecimal annualPercent) {
		return annualPercent.divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
	}

	/** The rate paid each month. */
	public BigDecimal monthlyReturnPercent() {
		return monthlyPercentOf(annualReturnPercent);
	}

	/** What {@code units} units cost. */
	public Money amountFor(int units) {
		return Money.of(unitPrice.multiply(BigDecimal.valueOf(units)), currency);
	}

	/** Why {@code units} units cannot be bought under this plan (empty when they can). */
	public List<String> unitProblems(int units) {
		if (units < minUnits) {
			return List.of("Buy at least " + minUnits + (minUnits == 1 ? " unit" : " units") + " of this plan");
		}
		return List.of();
	}

	/** Returns a month on {@code amount} at {@code rentPercent}, rounded to the currency's minor unit. */
	public static Money monthlyRent(Money amount, BigDecimal rentPercent) {
		return percentOf(amount, rentPercent);
	}

	/** Everything paid on {@code amount} over a term of {@code tenureYears}: the returns for every month of it. */
	public static Money totalPayout(Money amount, int tenureYears, BigDecimal rentPercent) {
		return monthlyRent(amount, rentPercent).times(BigDecimal.valueOf(monthsOf(tenureYears)));
	}

	public static int monthsOf(int years) {
		return years * 12;
	}

	/**
	 * Whether terms with this capital return percentage pay the price back over the tenure: only those sold
	 * before capital returns ended (100 ÷ tenure %); current plans carry zero.
	 */
	public static boolean returnsCapital(BigDecimal monthlyCapitalReturnPercent) {
		return monthlyCapitalReturnPercent != null && monthlyCapitalReturnPercent.signum() > 0;
	}

	/** The capital paid back over the whole tenure: the price under earlier terms, otherwise nothing. */
	public static Money capitalReturned(Money price, BigDecimal monthlyCapitalReturnPercent) {
		return returnsCapital(monthlyCapitalReturnPercent) ? price : Money.zero(price.currency());
	}

	/** 100 ÷ tenure: the share of the price returned each month under earlier terms, in percent to four decimals. */
	public static BigDecimal capitalReturnPercent(int tenureMonths) {
		return BigDecimal.valueOf(100).divide(BigDecimal.valueOf(tenureMonths), 4, RoundingMode.HALF_UP);
	}

	/** Price ÷ tenure, rounded half-up to the currency's minor unit. */
	public static Money capitalPerMonth(Money price, int tenureMonths) {
		int digits = Math.max(price.currency().getDefaultFractionDigits(), 0);
		return Money.of(price.amount().divide(BigDecimal.valueOf(tenureMonths), digits, RoundingMode.HALF_UP),
				price.currency());
	}

	/**
	 * The capital returned in month {@code n} (1-based): price ÷ tenure, except that the last month
	 * pays whatever rounding left over, so the months add up to exactly the price.
	 */
	public static Money capitalInstallment(Money price, int tenureMonths, int n) {
		Money monthly = capitalPerMonth(price, tenureMonths);
		return n < tenureMonths ? monthly : price.minus(monthly.times(BigDecimal.valueOf(tenureMonths - 1)));
	}

	/** {@code percent} % of {@code amount}, rounded half-up to the currency's minor unit. */
	public static Money percentOf(Money amount, BigDecimal percent) {
		Money exact = Money.of(amount.amount().multiply(percent).movePointLeft(2), amount.currency());
		return Money.of(exact.toMinorUnitScale(), amount.currency());
	}

}
