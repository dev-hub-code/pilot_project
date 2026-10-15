package com.sealease.backend.earning.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentCreditPropertiesTest {

	@Test
	void hourlyStepsStartLeasesAtTheNextFullHour() {
		RentCreditProperties hourly = new RentCreditProperties(Duration.ofHours(1));
		assertThat(hourly.stepStart(Instant.parse("2026-10-14T09:13:43Z"))).isEqualTo("2026-10-14T09:00:00Z");
		assertThat(hourly.nextStepFrom(Instant.parse("2026-10-14T09:13:43Z"))).isEqualTo("2026-10-14T10:00:00Z");
		assertThat(hourly.nextStepFrom(Instant.parse("2026-10-14T10:00:00Z"))).isEqualTo("2026-10-14T10:00:00Z");
	}

	@Test
	void fiveMinuteStepsForTesting() {
		RentCreditProperties quick = new RentCreditProperties(Duration.ofMinutes(5));
		assertThat(quick.stepStart(Instant.parse("2026-10-14T09:13:43Z"))).isEqualTo("2026-10-14T09:10:00Z");
		assertThat(quick.nextStepFrom(Instant.parse("2026-10-14T09:13:43Z"))).isEqualTo("2026-10-14T09:15:00Z");
	}

	@Test
	void stepsAreWholeMinutesThatDivideADay() {
		assertThatThrownBy(() -> new RentCreditProperties(Duration.ofSeconds(30))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RentCreditProperties(Duration.ofMinutes(7))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RentCreditProperties(Duration.ofSeconds(90))).isInstanceOf(IllegalArgumentException.class);
	}

}
