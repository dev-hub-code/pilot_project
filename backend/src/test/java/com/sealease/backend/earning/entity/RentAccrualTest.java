package com.sealease.backend.earning.entity;

import com.sealease.backend.common.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentAccrualTest {

	private static final Money RENT = Money.of("1000", "INR");
	private static final Money NONE = Money.zero(RENT.currency());

	private static RentAccrual lease(String start, int tenure, Money rent, Money capital) {
		return new RentAccrual(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse(start), tenure, rent,
				capital);
	}

	@Test
	void aLeaseRunsTheTenureFromItsFirstHour() {
		RentAccrual lease = lease("2026-10-05T16:00:00Z", 12, RENT, NONE);

		assertThat(lease.getEndsAt()).isEqualTo("2027-10-05T16:00:00Z");
		assertThat(lease.totalRent()).isEqualTo(Money.of("12000", "INR"));
		assertThat(lease.rentUpTo(Instant.parse("2026-10-05T16:00:00Z"))).isEqualTo(NONE);
		// One lease month (5 October 16:00 to 5 November 16:00) is a month's rent, however many days it has.
		assertThat(lease.rentUpTo(Instant.parse("2026-11-05T16:00:00Z"))).isEqualTo(RENT);
		assertThat(lease.rentUpTo(Instant.parse("2027-03-05T16:00:00Z"))).isEqualTo(Money.of("5000", "INR"));
		// Half of the first lease month (744 hours): 372 hours.
		assertThat(lease.rentUpTo(Instant.parse("2026-10-05T16:00:00Z").plus(Duration.ofHours(372))))
			.isEqualTo(Money.of("500", "INR"));
		// Nothing after the end.
		assertThat(lease.rentUpTo(Instant.parse("2030-01-01T00:00:00Z"))).isEqualTo(Money.of("12000", "INR"));
	}

	@Test
	void leaseMonthsAreCountedFromTheStartNotMonthAfterMonth() {
		// From 31 January: the lease months end on 28 February, 31 March, 30 April...
		RentAccrual lease = lease("2027-01-31T00:00:00Z", 3, RENT, NONE);
		assertThat(lease.monthsAt(Instant.parse("2027-02-28T00:00:00Z"))).isEqualByComparingTo("1");
		assertThat(lease.monthsAt(Instant.parse("2027-03-31T00:00:00Z"))).isEqualByComparingTo("2");
		assertThat(lease.getEndsAt()).isEqualTo("2027-04-30T00:00:00Z");
		assertThat(lease.monthsAt(lease.getEndsAt())).isEqualByComparingTo("3");
	}

	@Test
	void creditingHourByHourAddsUpToExactlyTheTenureWithCapital() {
		Money rent = Money.of("1234.57", "INR");
		Money capital = Money.of("99999.99", "INR");
		RentAccrual lease = lease("2027-01-29T07:00:00Z", 7, rent, capital);
		Money credited = NONE;
		Money returned = NONE;
		// Every hour on its own, in uneven runs of 1 to 50 hours.
		Instant at = lease.getStartsAt();
		int step = 1;
		while (!lease.isCompleted()) {
			Instant until = lease.creditableUntil(at.plus(Duration.ofHours(step)));
			Money[] amounts = lease.amountsUpTo(until);
			assertThat(amounts[0].isNegative()).isFalse();
			assertThat(amounts[1].isNegative()).isFalse();
			lease.creditUpTo(until, amounts[0], amounts[1], until);
			credited = credited.plus(amounts[0]);
			returned = returned.plus(amounts[1]);
			at = until;
			step = step % 50 + 1;
		}
		assertThat(credited).isEqualTo(rent.times(java.math.BigDecimal.valueOf(7)));
		assertThat(returned).isEqualTo(capital);
		assertThat(lease.creditedRent()).isEqualTo(credited);
		assertThat(lease.getCompletedAt()).isEqualTo(lease.getEndsAt());
		assertThat(lease.getEndsAt().atZone(ZoneOffset.UTC).getMonthValue()).isEqualTo(8);
	}

	@Test
	void aLeaseStartsOnAWholeMinuteAndIsCreditedForward() {
		assertThatThrownBy(() -> lease("2026-10-05T16:40:30Z", 12, RENT, NONE)).isInstanceOf(IllegalArgumentException.class);
		RentAccrual lease = lease("2026-10-05T16:00:00Z", 12, RENT, NONE);
		assertThatThrownBy(() -> lease.creditUpTo(lease.getStartsAt(), NONE, NONE, lease.getStartsAt()))
			.isInstanceOf(IllegalArgumentException.class);
		// Up to the start of the current credit step, within the lease.
		assertThat(lease.creditableUntil(Instant.parse("2026-10-05T18:00:00Z"))).isEqualTo("2026-10-05T18:00:00Z");
		assertThat(lease.creditableUntil(Instant.parse("2031-01-01T00:00:00Z"))).isEqualTo(lease.getEndsAt());
	}

}
