package com.sealease.backend.earning;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.earning.service.RentalSplit;
import com.sealease.backend.investment.dto.HoldingShare;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentalSplitTest {

	@Test
	void splitsByAmountNetOfTheFeeAndKeepsTheUnsoldShare() {
		RentalSplit split = RentalSplit.of(usd("1000"), usd("10000"), new BigDecimal("10"),
				List.of(holder("5000"), holder("3000")));

		assertThat(split.shares()).extracting(s -> s.gross().amount().toPlainString())
			.containsExactly("500.0000", "300.0000");
		assertThat(split.shares()).extracting(s -> s.fee().amount().toPlainString())
			.containsExactly("50.0000", "30.0000");
		assertThat(split.shares()).extracting(s -> s.net().amount().toPlainString())
			.containsExactly("450.0000", "270.0000");
		assertThat(split.fees()).isEqualTo(usd("80"));
		assertThat(split.retained()).isEqualTo(usd("200"));
		assertBalanced(split);
	}

	@Test
	void roundsSharesDownToCentsAndRetainsTheRemainder() {
		RentalSplit split = RentalSplit.of(usd("1000"), usd("3000"), new BigDecimal("7.5"),
				List.of(holder("1000"), holder("1000"), holder("1000")));

		// 1000 ÷ 3 = 333.333… → 333.33 each; 0.01 is left over. Fee 7.5% of 333.33 = 24.99975 → 25.00.
		assertThat(split.shares()).allSatisfy(s -> {
			assertThat(s.gross()).isEqualTo(usd("333.33"));
			assertThat(s.fee()).isEqualTo(usd("25.00"));
			assertThat(s.net()).isEqualTo(usd("308.33"));
		});
		assertThat(split.retained()).isEqualTo(usd("0.01"));
		assertBalanced(split);
	}

	@Test
	void respectsCurrenciesWithoutMinorUnits() {
		RentalSplit split = RentalSplit.of(Money.of("100001", "JPY"), Money.of("3000000", "JPY"), new BigDecimal("5"),
				List.of(new HoldingShare(UUID.randomUUID(), UUID.randomUUID(), Money.of("1000000", "JPY"),
						new BigDecimal("33.3333"))));

		assertThat(split.shares().getFirst().gross()).isEqualTo(Money.of("33333", "JPY"));
		assertThat(split.shares().getFirst().fee()).isEqualTo(Money.of("1667", "JPY"));
		assertBalanced(split);
	}

	@Test
	void withoutHoldersEverythingIsRetained() {
		RentalSplit split = RentalSplit.of(usd("1000"), usd("10000"), BigDecimal.ZERO, List.of());

		assertThat(split.shares()).isEmpty();
		assertThat(split.retained()).isEqualTo(usd("1000"));
	}

	@Test
	void refusesHoldingsBeyondThePrice() {
		assertThatThrownBy(() -> RentalSplit.of(usd("1000"), usd("10000"), BigDecimal.ZERO,
				List.of(holder("6000"), holder("6000"))))
			.isInstanceOf(IllegalStateException.class);
	}

	private static void assertBalanced(RentalSplit split) {
		Money net = split.shares().stream().map(RentalSplit.Share::net).reduce(Money::plus).orElseThrow();
		assertThat(net.plus(split.fees()).plus(split.retained())).isEqualTo(split.received());
	}

	private static HoldingShare holder(String amount) {
		return new HoldingShare(UUID.randomUUID(), UUID.randomUUID(), usd(amount), BigDecimal.ONE);
	}

	private static Money usd(String amount) {
		return Money.of(amount, "USD");
	}

}
