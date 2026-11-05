package com.sealease.backend.withdrawal.service;

import com.sealease.backend.common.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawalCalculatorTest {

	private static final Currency INR = Currency.getInstance("INR");
	private static final Money MINIMUM = inr("50");

	@Test
	void noChargesPayTheWholeAmount() {
		var q = WithdrawalCalculator.quote(inr("1000"), new BigDecimal("400"), MINIMUM, WithdrawalCalculator.Charges.NONE);
		assertThat(q.allowed()).isTrue();
		assertThat(q.net()).isEqualTo(inr("400"));
		assertThat(q.remaining()).isEqualTo(inr("600"));
	}

	@Test
	void chargesAndTaxComeOutOfTheAmountRoundedHalfUpToThePaisa() {
		var charges = new WithdrawalCalculator.Charges(new BigDecimal("5"), new BigDecimal("1.5"), new BigDecimal("2"));
		var q = WithdrawalCalculator.quote(inr("1000"), new BigDecimal("333.33"), MINIMUM, charges);
		// 5 + 1.5% of 333.33 = 5 + 4.99995 → 10.00; 2% = 6.6666 → 6.67
		assertThat(q.fee()).isEqualTo(inr("10.00"));
		assertThat(q.tax()).isEqualTo(inr("6.67"));
		assertThat(q.net()).isEqualTo(inr("316.66"));
		assertThat(q.fee().plus(q.tax()).plus(q.net())).isEqualTo(q.amount());
	}

	@Test
	void refusesBelowTheMinimumAboveTheBalanceFractionsOfAPaisaAndNothingToPay() {
		assertThat(quote("49.99", WithdrawalCalculator.Charges.NONE).problem()).contains("minimum");
		assertThat(quote("1000.01", WithdrawalCalculator.Charges.NONE).problem()).contains("available");
		assertThat(quote("100.001", WithdrawalCalculator.Charges.NONE).problem()).contains("paise");
		assertThat(quote("0", WithdrawalCalculator.Charges.NONE).allowed()).isFalse();
		assertThat(quote("-5", WithdrawalCalculator.Charges.NONE).allowed()).isFalse();
		var steep = new WithdrawalCalculator.Charges(new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
		assertThat(quote("100", steep).problem()).contains("nothing to pay");
	}

	@Test
	void theWholeBalanceCanBeWithdrawn() {
		var q = quote("1000", WithdrawalCalculator.Charges.NONE);
		assertThat(q.allowed()).isTrue();
		assertThat(q.remaining().isZero()).isTrue();
	}

	private static WithdrawalCalculator.Quote quote(String amount, WithdrawalCalculator.Charges charges) {
		return WithdrawalCalculator.quote(inr("1000"), new BigDecimal(amount), MINIMUM, charges);
	}

	private static Money inr(String amount) {
		return Money.of(new BigDecimal(amount), INR);
	}

}
