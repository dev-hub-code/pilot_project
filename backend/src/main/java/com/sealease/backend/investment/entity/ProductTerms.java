package com.sealease.backend.investment.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.container.entity.ContainerType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * The commercial terms of a plan, as investors see and accept them. Each container bought costs
 * {@code price}; for {@code tenureMonths} months the investor is then paid, per container,
 * {@code monthlyRentPercent} of the price as rent plus the price ÷ tenure as capital returned, so the
 * whole price comes back by the end of the lease. {@code monthlyCapitalReturnPercent} is
 * 100 ÷ tenure, to four decimals.
 */
public record ProductTerms(
		ContainerType containerType,
		String title,
		String summary,
		String description,
		Currency currency,
		BigDecimal price,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		int tenureMonths,
		String riskDisclosure,
		String termsAndConditions) {

	public Money pricePerContainer() {
		return Money.of(price, currency);
	}

	/** Rent per container per month, rounded to the currency's minor unit. */
	public Money monthlyRent() {
		return percentOfPrice(monthlyRentPercent);
	}

	/** Capital returned per container per month (the last month absorbs the rounding). */
	public Money monthlyCapitalReturn() {
		return capitalPerMonth(pricePerContainer(), tenureMonths);
	}

	public Money monthlyPayout() {
		return monthlyRent().plus(monthlyCapitalReturn());
	}

	/** Everything paid per container over the tenure: all the rent plus the whole price back. */
	public Money totalPayout() {
		return monthlyRent().times(BigDecimal.valueOf(tenureMonths)).plus(pricePerContainer());
	}

	/** Monthly rent and capital return together, as a percentage of the price. */
	public BigDecimal monthlyPayoutPercent() {
		return monthlyRentPercent.add(monthlyCapitalReturnPercent);
	}

	private Money percentOfPrice(BigDecimal percent) {
		return percentOf(pricePerContainer(), percent);
	}

	/** 100 ÷ tenure: the share of the price returned each month, in percent to four decimals. */
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
