package com.sealease.backend.investment.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.container.entity.ContainerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Per-container payout arithmetic of a plan: rent % plus 100 ÷ tenure % of the price back each month. */
class ProductTermsTest {

	private static final Currency INR = Currency.getInstance("INR");

	@Test
	void monthlyPayoutIsRentPlusTheWholePriceSpreadOverTheTenure() {
		ProductTerms t = terms("500000", "2", 16);
		assertThat(t.monthlyCapitalReturnPercent()).isEqualByComparingTo("6.25");
		assertThat(t.monthlyRent()).isEqualTo(Money.of("10000", "INR"));
		assertThat(t.monthlyCapitalReturn()).isEqualTo(Money.of("31250", "INR"));
		assertThat(t.monthlyPayout()).isEqualTo(Money.of("41250", "INR"));
		assertThat(t.monthlyPayoutPercent()).isEqualByComparingTo("8.25");
		// 16 months of rent (32% of the price) plus the whole price back.
		assertThat(t.totalPayout()).isEqualTo(Money.of("660000", "INR"));
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
		assertThat(terms("33333.33", "1.75", 16).monthlyRent()).isEqualTo(Money.of("583.33", "INR"));
		assertThat(ProductTerms.percentOf(Money.of("10.10", "INR"), new BigDecimal("5"))).isEqualTo(Money.of("0.51", "INR"));
	}

	private static ProductTerms terms(String price, String rent, int months) {
		return new ProductTerms(ContainerType.DRY_20FT, "Title", "Summary", "Description", INR, new BigDecimal(price),
				new BigDecimal(rent), ProductTerms.capitalReturnPercent(months), months, "Risk", "Terms");
	}

}
