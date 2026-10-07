package com.sealease.backend.earning;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.dto.Lease;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.RentalFrequency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeaseScheduleTest {

	@Test
	void countsOnlyWholePeriodsWithinTheTerm() {
		assertThat(Lease.periodCount(RentalFrequency.MONTHLY, 13)).isEqualTo(13);
		assertThat(Lease.periodCount(RentalFrequency.QUARTERLY, 13)).isEqualTo(4);
		assertThat(Lease.periodCount(RentalFrequency.QUARTERLY, 2)).isZero();
	}

	@Test
	void periodsRunFromTheLeaseStartWithoutDriftingAtMonthEnds() {
		Lease lease = lease(RentalFrequency.MONTHLY, 12, LocalDate.of(2027, 1, 31));

		assertThat(lease.period(1).orElseThrow()).isEqualTo(
				new Lease.RentalPeriod(1, LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28)));
		// Computed from the start each time: March 31st, not March 28th.
		assertThat(lease.period(3).orElseThrow().startsOn()).isEqualTo(LocalDate.of(2027, 3, 31));
		assertThat(lease.period(12).orElseThrow().endsOn()).isEqualTo(LocalDate.of(2028, 1, 31));
		assertThat(lease.period(0)).isEmpty();
		assertThat(lease.period(13)).isEmpty();
	}

	@Test
	void rentFallsDueWhenThePeriodHasEnded() {
		Lease.RentalPeriod first = lease(RentalFrequency.QUARTERLY, 12, LocalDate.of(2027, 1, 1)).period(1).orElseThrow();

		assertThat(first.endsOn()).isEqualTo(LocalDate.of(2027, 4, 1));
		assertThat(first.isDueOn(LocalDate.of(2027, 3, 31))).isFalse();
		assertThat(first.isDueOn(LocalDate.of(2027, 4, 1))).isTrue();
	}

	private static Lease lease(RentalFrequency frequency, int months, LocalDate startsOn) {
		return new Lease(UUID.randomUUID(), "CONT-1", "Test", ProductStatus.ACTIVE, Money.of("10000", "USD"),
				Money.of("100", "USD"), frequency, months, BigDecimal.ZERO, startsOn, startsOn.plusMonths(months));
	}

}
