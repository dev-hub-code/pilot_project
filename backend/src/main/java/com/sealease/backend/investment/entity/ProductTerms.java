package com.sealease.backend.investment.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.container.entity.ContainerType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;

/**
 * The commercial terms of a plan, as investors see and accept them. Each container bought costs
 * {@code price}; the investor chooses a lease of whole years between {@code minTenureYears} and
 * {@code maxTenureYears}, and for every month of it is paid, per container, {@code monthlyRentPercent}
 * of the price as rent. The price is not paid back: plans carry a {@code monthlyCapitalReturnPercent}
 * of zero. GST at {@code gstRatePercent} is charged on the price when the container is bought; rent is on the
 * price without GST.
 *
 * <p>Plans sold before that returned the whole price over the tenure as well (price ÷ tenure a month,
 * {@code monthlyCapitalReturnPercent} = 100 ÷ tenure). Holdings bought under those terms keep them, so the
 * percentage recorded with a purchase decides whether it returns capital ({@link #returnsCapital}).
 */
public record ProductTerms(
		ContainerType containerType,
		String title,
		String summary,
		String description,
		Currency currency,
		BigDecimal price,
		BigDecimal gstRatePercent,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		int minTenureYears,
		int maxTenureYears,
		String riskDisclosure,
		String termsAndConditions) {

	/** The price per container, without GST: what rent and referral commission are based on. */
	public Money pricePerContainer() {
		return Money.of(price, currency);
	}

	/** GST on the price of one container, rounded half-up to the paisa. */
	public Money gstPerContainer() {
		return gstOn(pricePerContainer(), gstRatePercent);
	}

	/** What the investor pays per container: the price plus GST. */
	public Money pricePerContainerWithGst() {
		return pricePerContainer().plus(gstPerContainer());
	}

	/** GST at {@code ratePercent} on {@code amount}, rounded half-up to the currency's minor unit. */
	public static Money gstOn(Money amount, BigDecimal ratePercent) {
		return percentOf(amount, ratePercent == null ? BigDecimal.ZERO : ratePercent);
	}

	/** Rent per container per month, rounded to the currency's minor unit. */
	public Money monthlyRent() {
		return percentOfPrice(monthlyRentPercent);
	}

	/** Capital returned per container per month: none, plans pay rent only (enforced by the database). */
	public Money monthlyCapitalReturn() {
		return Money.zero(currency);
	}

	public Money monthlyPayout() {
		return monthlyRent().plus(monthlyCapitalReturn());
	}

	/** Everything paid per container over a lease of {@code tenureYears}: the rent for every month of it. */
	public Money totalPayout(int tenureYears) {
		return monthlyRent().times(BigDecimal.valueOf(monthsOf(tenureYears)));
	}

	/** The shortest lease, in months. */
	public int minTenureMonths() {
		return monthsOf(minTenureYears);
	}

	/** Why a lease of {@code tenureYears} cannot be chosen under this plan (empty when it can). */
	public List<String> tenureProblems(int tenureYears) {
		if (tenureYears < minTenureYears || tenureYears > maxTenureYears) {
			return List.of(minTenureYears == maxTenureYears
					? "This plan's lease is " + years(minTenureYears)
					: "Choose a lease of " + minTenureYears + " to " + maxTenureYears + " years");
		}
		return List.of();
	}

	public static int monthsOf(int years) {
		return years * 12;
	}

	private static String years(int n) {
		return n == 1 ? "1 year" : n + " years";
	}

	/** Monthly rent and capital return together, as a percentage of the price. */
	public BigDecimal monthlyPayoutPercent() {
		return monthlyRentPercent.add(monthlyCapitalReturnPercent);
	}

	private Money percentOfPrice(BigDecimal percent) {
		return percentOf(pricePerContainer(), percent);
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
