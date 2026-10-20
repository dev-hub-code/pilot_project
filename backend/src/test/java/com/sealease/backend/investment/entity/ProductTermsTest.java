package com.sealease.backend.investment.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.container.entity.ContainerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-container payout arithmetic of a plan: rent % a month for a lease of whole years the investor chooses
 * within the plan's limits, with no capital back; and the capital arithmetic that holdings sold before capital
 * returns ended still use (100 ÷ tenure % of the price back each month).
 */
class ProductTermsTest {

	private static final Currency INR = Currency.getInstance("INR");

	@Test
	void plansPayRentOnlyForTheLeaseTheInvestorChooses() {
		ProductTerms t = terms("500000", 2, 5);
		BigDecimal rate = new BigDecimal("2");
		assertThat(t.monthlyRent(rate)).isEqualTo(Money.of("10000", "INR"));
		assertThat(t.minTenureMonths()).isEqualTo(24);
		// 2 years: 24 months of rent (48% of the price); 5 years: 60 months. The price is not paid back.
		assertThat(t.totalPayout(2, rate)).isEqualTo(Money.of("240000", "INR"));
		assertThat(t.totalPayout(5, rate)).isEqualTo(Money.of("600000", "INR"));
	}

	@Test
	void theLeaseMustBeWithinThePlansLimits() {
		ProductTerms t = terms("500000", 2, 5);
		assertThat(t.tenureProblems(2)).isEmpty();
		assertThat(t.tenureProblems(5)).isEmpty();
		assertThat(t.tenureProblems(1)).containsExactly("Choose a lease of 2 to 5 years");
		assertThat(t.tenureProblems(6)).containsExactly("Choose a lease of 2 to 5 years");
		assertThat(terms("500000", 3, 3).tenureProblems(4)).containsExactly("The lease for this container is 3 years");
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
		assertThat(terms("33333.33", 1, 2).monthlyRent(new BigDecimal("1.75"))).isEqualTo(Money.of("583.33", "INR"));
		assertThat(ProductTerms.percentOf(Money.of("10.10", "INR"), new BigDecimal("5"))).isEqualTo(Money.of("0.51", "INR"));
	}

	private static ProductTerms terms(String price, int minYears, int maxYears) {
		return new ProductTerms(ContainerType.DRY_20FT, "Title", "Summary", "Description", INR, new BigDecimal(price),
				BigDecimal.ZERO, minYears, maxYears, "Risk", "Terms");
	}

}
