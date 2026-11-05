package com.sealease.backend.investment.entity;

import com.sealease.backend.common.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-unit payout arithmetic of a plan: rent % a month for a lease of whole years the investor chooses
 * within the plan's limits, with no capital back; and the capital arithmetic that holdings sold before capital
 * returns ended still use (100 ÷ tenure % of the price back each month).
 */
class ProductTermsTest {

	private static final Currency INR = Currency.getInstance("INR");

	@Test
	void unitsCostTheUnitPriceAndPayTheMonthlyReturnForTheTenure() {
		ProductTerms t = terms(3, "36", "500", 10);
		Money amount = t.amountFor(200);
		assertThat(amount).isEqualTo(Money.of("100000", "INR"));
		assertThat(ProductTerms.monthlyRent(amount, t.monthlyReturnPercent())).isEqualTo(Money.of("3000", "INR"));
		// 36 months of returns. The amount is not paid back.
		assertThat(ProductTerms.totalPayout(amount, t.tenureYears(), t.monthlyReturnPercent()))
			.isEqualTo(Money.of("108000", "INR"));
	}

	@Test
	void aPlanSetsTheFewestUnitsThatCanBeBought() {
		ProductTerms t = terms(3, "36", "500", 10);
		assertThat(t.unitProblems(10)).isEmpty();
		assertThat(t.unitProblems(9)).containsExactly("Buy at least 10 units of this plan");
		assertThat(terms(3, "36", "500", 1).unitProblems(0)).containsExactly("Buy at least 1 unit of this plan");
	}

	@Test
	void holdingsSoldBeforeCapitalReturnsEndedStillGetThePriceBack() {
		assertThat(ProductTerms.returnsCapital(BigDecimal.ZERO)).isFalse();
		assertThat(ProductTerms.returnsCapital(new BigDecimal("6.25"))).isTrue();
		Money price = Money.of("500000", "INR");
		assertThat(ProductTerms.capitalReturned(price, new BigDecimal("6.25"))).isEqualTo(price);
		assertThat(ProductTerms.capitalReturned(price, BigDecimal.ZERO)).isEqualTo(Money.zero(INR));
		assertThat(ProductTerms.capitalPerMonth(price, 16)).isEqualTo(Money.of("31250", "INR"));
	}

	@Test
	void theCapitalReturnFollowsTheTenure() {
		assertThat(ProductTerms.capitalReturnPercent(12)).isEqualByComparingTo("8.3333");
		assertThat(ProductTerms.capitalReturnPercent(10)).isEqualByComparingTo("10");
		assertThat(ProductTerms.capitalReturnPercent(1)).isEqualByComparingTo("100");
	}

	@Test
	void theLastMonthAbsorbsRoundingSoTheWholePriceComesBack() {
		Money price = Money.of("50000", "INR");
		// 50,000 ÷ 12 = 4,166.666…: eleven months of 4,166.67 and a last month of 4,166.63.
		assertThat(ProductTerms.capitalPerMonth(price, 12)).isEqualTo(Money.of("4166.67", "INR"));
		assertThat(ProductTerms.capitalInstallment(price, 12, 11)).isEqualTo(Money.of("4166.67", "INR"));
		assertThat(ProductTerms.capitalInstallment(price, 12, 12)).isEqualTo(Money.of("4166.63", "INR"));
		Money total = IntStream.rangeClosed(1, 12).mapToObj(n -> ProductTerms.capitalInstallment(price, 12, n))
			.reduce(Money.zero(INR), Money::plus);
		assertThat(total).isEqualTo(price);
	}

	@Test
	void rentIsRoundedHalfUpToThePaisa() {
		assertThat(ProductTerms.monthlyRent(Money.of("33333.33", "INR"), new BigDecimal("1.75"))).isEqualTo(Money.of("583.33", "INR"));
		assertThat(ProductTerms.percentOf(Money.of("10.10", "INR"), new BigDecimal("5"))).isEqualTo(Money.of("0.51", "INR"));
	}

	private static ProductTerms terms(int years, String percent, String unitPrice, int minUnits) {
		return new ProductTerms("Title", "Summary", INR, BigDecimal.ZERO, years, new BigDecimal(percent),
				new BigDecimal(unitPrice), minUnits);
	}

}
