package com.sealease.backend.investment.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.entity.RentalFrequency;
import com.sealease.backend.investment.entity.RiskLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

/** The spec's worked example: a $50,000 container renting at $1,500/month. */
class InvestmentAmountPolicyTest {

	private static final Currency USD = Currency.getInstance("USD");

	private final ProductTerms retail = terms(InvestmentType.RETAIL, "1000", "500", "20000");
	private final ProductTerms standalone = terms(InvestmentType.HNI, "50000", "50000", null);

	@Test
	void acceptsValidRetailAmounts() {
		assertThat(violations(retail, "1000", "32000", "0")).isEmpty();
		assertThat(violations(retail, "10500", "32000", "0")).isEmpty();
	}

	@Test
	void enforcesMinimumAndIncrement() {
		assertThat(violations(retail, "999", "32000", "0")).containsExactly("Minimum investment is 1000.00 USD");
		assertThat(violations(retail, "1250", "32000", "0")).singleElement().asString().contains("multiples of 500");
	}

	@Test
	void cannotExceedAvailability() {
		assertThat(violations(retail, "7000", "6000", "0")).containsExactly("Only 6000.00 USD is still available");
		assertThat(violations(retail, "1000", "0", "0")).containsExactly("This offering has no capacity left");
	}

	@Test
	void enforcesMaximumPerInvestorIncludingExistingExposure() {
		assertThat(violations(retail, "5000", "32000", "15000")).isEmpty();
		assertThat(violations(retail, "5500", "32000", "15000")).singleElement().asString()
			.startsWith("Maximum per investor is 20000.00 USD");
	}

	@Test
	void rejectsFractionsOfACentAndOtherCurrencies() {
		assertThat(violations(retail, "1000.005", "32000", "0")).contains("Amount cannot have more than 2 decimal places");
		assertThat(InvestmentAmountPolicy.violations(retail, Money.of("1000", "EUR"), usd("32000"), usd("0")))
			.containsExactly("Amount must be in USD");
	}

	@Test
	void standaloneContainersAreBoughtInFull() {
		assertThat(violations(standalone, "50000", "50000", "0")).isEmpty();
		assertThat(violations(standalone, "25000", "50000", "0")).singleElement().asString()
			.startsWith("A standalone container must be purchased in full");
		assertThat(violations(standalone, "50000", "0", "0")).contains("This offering has no capacity left");
	}

	@Test
	void ownershipAndRentalShareAreExact() {
		assertThat(InvestmentAmountPolicy.ownershipPercent(retail, usd("10000"))).isEqualByComparingTo("20.0000");
		assertThat(InvestmentAmountPolicy.rentalShare(retail, usd("10000"))).isEqualTo(usd("300"));
		// 1/3 of the container: 1500 / 3 = 500 exactly; 33.3333% ownership rounded HALF_UP at 4 decimals.
		assertThat(InvestmentAmountPolicy.ownershipPercent(retail, usd("16666.67"))).isEqualByComparingTo("33.3333");
		assertThat(InvestmentAmountPolicy.rentalShare(retail, usd("16666.67"))).isEqualTo(usd("500.0001"));
	}

	@Test
	void expectedAnnualReturnIsGrossRentalYield() {
		assertThat(retail.expectedAnnualReturnPercent()).isEqualByComparingTo("36.00");
	}

	private static java.util.List<String> violations(ProductTerms terms, String amount, String available, String exposure) {
		return InvestmentAmountPolicy.violations(terms, usd(amount), usd(available), usd(exposure));
	}

	private static Money usd(String amount) {
		return Money.of(amount, "USD");
	}

	private static ProductTerms terms(InvestmentType type, String minimum, String increment, String maximum) {
		return new ProductTerms(type, "Title", "Summary", "Description", USD, new BigDecimal("50000"),
				new BigDecimal(minimum), new BigDecimal(increment), maximum == null ? null : new BigDecimal(maximum),
				new BigDecimal("1500"), RentalFrequency.MONTHLY, 36, BigDecimal.ZERO, null, RiskLevel.MEDIUM, "Risk", "Terms", "v1",
				null, null);
	}

}
