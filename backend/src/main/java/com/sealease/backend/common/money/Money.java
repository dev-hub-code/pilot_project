package com.sealease.backend.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary amount. The only type business modules should use for money.
 *
 * <p><b>Precision policy</b>: amounts are always held at {@link #SCALE} decimal places, matching
 * the {@code NUMERIC(19,4)} database columns, and every operation that can produce extra digits
 * rounds with {@link #ROUNDING}. Both constants are tied to the schema and therefore deliberately
 * not configurable. Rounding to a currency's minor unit (e.g. 2 places for USD) happens only at
 * payout/presentation boundaries via {@link #toMinorUnitScale()}.
 *
 * <p>Arithmetic between different currencies is rejected - there is no implicit FX conversion.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

	public static final int SCALE = 4;
	public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	public Money {
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(currency, "currency");
		amount = amount.setScale(SCALE, ROUNDING);
	}

	public static Money of(BigDecimal amount, Currency currency) {
		return new Money(amount, currency);
	}

	public static Money of(String amount, String currencyCode) {
		return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
	}

	public static Money zero(Currency currency) {
		return new Money(BigDecimal.ZERO, currency);
	}

	public Money plus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.add(other.amount), currency);
	}

	public Money minus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.subtract(other.amount), currency);
	}

	/** Multiplies by a factor (e.g. an ownership share or referral rate), rounding to {@link #SCALE}. */
	public Money times(BigDecimal factor) {
		Objects.requireNonNull(factor, "factor");
		return new Money(amount.multiply(factor), currency);
	}

	public Money negate() {
		return new Money(amount.negate(), currency);
	}

	public boolean isZero() {
		return amount.signum() == 0;
	}

	public boolean isPositive() {
		return amount.signum() > 0;
	}

	public boolean isNegative() {
		return amount.signum() < 0;
	}

	public boolean isGreaterThan(Money other) {
		return compareTo(other) > 0;
	}

	public boolean isLessThan(Money other) {
		return compareTo(other) < 0;
	}

	/** Rounds to the currency's minor unit (e.g. cents) for payouts and display. */
	public BigDecimal toMinorUnitScale() {
		int digits = Math.max(currency.getDefaultFractionDigits(), 0);
		return amount.setScale(digits, ROUNDING);
	}

	/** For messages shown to people: minor-unit scale, e.g. "1000.00 USD". */
	public String display() {
		return toMinorUnitScale().toPlainString() + " " + currency.getCurrencyCode();
	}

	@Override
	public int compareTo(Money other) {
		requireSameCurrency(other);
		return amount.compareTo(other.amount);
	}

	@Override
	public String toString() {
		return amount.toPlainString() + " " + currency.getCurrencyCode();
	}

	private void requireSameCurrency(Money other) {
		Objects.requireNonNull(other, "other");
		if (!currency.equals(other.currency)) {
			throw new CurrencyMismatchException(currency, other.currency);
		}
	}

}
