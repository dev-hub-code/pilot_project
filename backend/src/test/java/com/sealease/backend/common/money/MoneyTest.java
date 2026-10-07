package com.sealease.backend.common.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

	private static final Currency USD = Currency.getInstance("USD");

	@Test
	void normalisesToStorageScale() {
		assertThat(Money.of("10", "USD").amount()).isEqualByComparingTo("10.0000");
		assertThat(Money.of("10", "USD").amount().scale()).isEqualTo(Money.SCALE);
		assertThat(Money.of("1.23456", "USD").amount()).isEqualTo(new BigDecimal("1.2346"));
		assertThat(Money.of("1.23455", "USD").amount()).isEqualTo(new BigDecimal("1.2346"));
		assertThat(Money.of("1.23454", "USD").amount()).isEqualTo(new BigDecimal("1.2345"));
	}

	@Test
	void valuesWithDifferentInputScaleAreEqual() {
		assertThat(Money.of("5.5", "USD")).isEqualTo(Money.of("5.5000", "USD"));
	}

	@Test
	void addsAndSubtracts() {
		Money a = Money.of("800.00", "USD");
		Money b = Money.of("200.00", "USD");
		assertThat(a.plus(b)).isEqualTo(Money.of("1000", "USD"));
		assertThat(b.minus(a)).isEqualTo(Money.of("-600", "USD"));
		assertThat(b.minus(a).isNegative()).isTrue();
	}

	@Test
	void appliesReferralRatesExactly() {
		Money rental = Money.of("1000", "USD");
		assertThat(rental.times(new BigDecimal("0.02"))).isEqualTo(Money.of("20", "USD"));
		assertThat(rental.times(new BigDecimal("0.01"))).isEqualTo(Money.of("10", "USD"));
		assertThat(rental.times(new BigDecimal("0.005"))).isEqualTo(Money.of("5", "USD"));
	}

	@Test
	void multiplicationRoundsHalfUpAtStorageScale() {
		Money amount = Money.of("0.0001", "USD");
		assertThat(amount.times(new BigDecimal("0.5")).amount()).isEqualTo(new BigDecimal("0.0001"));
		assertThat(amount.times(new BigDecimal("0.49")).amount()).isEqualTo(new BigDecimal("0.0000"));
	}

	@Test
	void roundsToCurrencyMinorUnitForPayout() {
		assertThat(Money.of("10.1250", "USD").toMinorUnitScale()).isEqualTo(new BigDecimal("10.13"));
		assertThat(Money.of("10.1249", "USD").toMinorUnitScale()).isEqualTo(new BigDecimal("10.12"));
		assertThat(Money.of("1500.5000", "JPY").toMinorUnitScale()).isEqualTo(new BigDecimal("1501"));
	}

	@Test
	void comparesWithinSameCurrency() {
		assertThat(Money.of("7000", "USD").isGreaterThan(Money.of("6999.9999", "USD"))).isTrue();
		assertThat(Money.of("7000", "USD").isLessThan(Money.of("7000", "USD"))).isFalse();
		assertThat(Money.zero(USD).isZero()).isTrue();
	}

	@Test
	void rejectsMixedCurrencyArithmetic() {
		Money usd = Money.of("10", "USD");
		Money eur = Money.of("10", "EUR");
		assertThatThrownBy(() -> usd.plus(eur)).isInstanceOf(CurrencyMismatchException.class);
		assertThatThrownBy(() -> usd.minus(eur)).isInstanceOf(CurrencyMismatchException.class);
		assertThatThrownBy(() -> usd.compareTo(eur)).isInstanceOf(CurrencyMismatchException.class);
	}

	@Test
	void rejectsNulls() {
		assertThatThrownBy(() -> Money.of(null, USD)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Money.of(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
	}

}
