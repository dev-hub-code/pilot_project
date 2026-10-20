package com.sealease.backend.earning.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditIntervalTest {

	@Test
	void stepsLineUpWithHoursAndDaysInUtc() {
		Instant at = Instant.parse("2026-10-19T10:37:12Z");
		assertThat(CreditInterval.ofMinutes(60).stepStart(at)).isEqualTo(Instant.parse("2026-10-19T10:00:00Z"));
		assertThat(CreditInterval.ofMinutes(360).stepStart(at)).isEqualTo(Instant.parse("2026-10-19T06:00:00Z"));
		assertThat(CreditInterval.ofMinutes(1440).stepStart(at)).isEqualTo(Instant.parse("2026-10-19T00:00:00Z"));
		assertThat(CreditInterval.ofMinutes(1440).nextBoundaryAfter(Instant.parse("2026-10-19T10:00:00Z")))
			.isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
		assertThat(CreditInterval.ofMinutes(60).nextBoundaryAfter(Instant.parse("2026-10-19T10:00:00Z")))
			.as("from a boundary, the next one").isEqualTo(Instant.parse("2026-10-19T11:00:00Z"));
		assertThat(CreditInterval.ofMinutes(15).nextStepFrom(at)).isEqualTo(Instant.parse("2026-10-19T10:45:00Z"));
	}

	@Test
	void labelsReadNaturally() {
		assertThat(CreditInterval.ofMinutes(60).label()).isEqualTo("every hour");
		assertThat(CreditInterval.ofMinutes(360).label()).isEqualTo("every 6 hours");
		assertThat(CreditInterval.ofMinutes(1440).label()).isEqualTo("every day");
		assertThat(CreditInterval.ofMinutes(15).label()).isEqualTo("every 15 minutes");
	}

	@Test
	void onlyWholeMinutesDividingADay() {
		assertThatThrownBy(() -> CreditInterval.ofMinutes(7)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CreditInterval(Duration.ofSeconds(90))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CreditInterval.ofMinutes(2880)).isInstanceOf(IllegalArgumentException.class);
		assertThat(RentCreditSettingsService.OPTIONS).allSatisfy(m -> CreditInterval.ofMinutes(m));
	}

}
