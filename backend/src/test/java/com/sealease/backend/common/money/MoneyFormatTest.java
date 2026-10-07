package com.sealease.backend.common.money;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyFormatTest {

	@Test
	void rupeesUseTheSymbolAndIndianGrouping() {
		assertThat(Money.of("0", "INR").display()).isEqualTo("₹0.00");
		assertThat(Money.of("999.5", "INR").display()).isEqualTo("₹999.50");
		assertThat(Money.of("1000", "INR").display()).isEqualTo("₹1,000.00");
		assertThat(Money.of("123456.789", "INR").display()).isEqualTo("₹1,23,456.79");
		assertThat(Money.of("12345678", "INR").display()).isEqualTo("₹1,23,45,678.00");
		assertThat(Money.of("-250000", "INR").display()).isEqualTo("-₹2,50,000.00");
	}

	@Test
	void otherCurrenciesGroupInThousands() {
		assertThat(Money.of("1234567.5", "USD").display()).isEqualTo("$1,234,567.50");
	}

}
