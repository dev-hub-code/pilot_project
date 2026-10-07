package com.sealease.backend.referral;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.referral.entity.ReferralRateVersion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferralRateVersionTest {

	private final ReferralRateVersion rates = new ReferralRateVersion(Instant.EPOCH,
			List.of(new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("0.5"), new BigDecimal("0.125")),
			"Test", null, Instant.EPOCH);

	@Test
	void appliesEachLevelsRate() {
		Money base = Money.of("1000", "USD");
		assertThat(rates.commission(1, base)).isEqualTo(Money.of("20", "USD"));
		assertThat(rates.commission(2, base)).isEqualTo(Money.of("10", "USD"));
		assertThat(rates.commission(3, base)).isEqualTo(Money.of("5", "USD"));
		assertThat(rates.commission(4, base)).isEqualTo(Money.of("1.25", "USD"));
	}

	@Test
	void roundsDownToTheMinorUnit() {
		// 333.33 × 0.125% = 0.41666… → 0.41; 0.39 × 0.125% = 0.0004875 → nothing to pay.
		assertThat(rates.commission(4, Money.of("333.33", "USD"))).isEqualTo(Money.of("0.41", "USD"));
		assertThat(rates.commission(4, Money.of("0.39", "USD")).isZero()).isTrue();
		assertThat(rates.commission(1, Money.of("12345", "JPY"))).isEqualTo(Money.of("246", "JPY"));
	}

	@Test
	void knowsOnlyFourLevels() {
		assertThatThrownBy(() -> rates.percentFor(5)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ReferralRateVersion(Instant.EPOCH, List.of(BigDecimal.ONE), "Test", null,
				Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
	}

}
