package com.sealease.backend.earning.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.earning.service.PayoutSchedule.Entry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutScheduleTest {

	private static final Money RENT = Money.of("1000", "INR");
	private static final Money PRICE = Money.of("12000", "INR");

	@Test
	void aLeaseStartingBeforeThePayoutDayIsPaidAPartMonthFirstAndLast() {
		// Bought on 5 October, paid on the 10th: 5 of the 30 days from 10 September to 10 October.
		List<Entry> plan = PayoutSchedule.plan(LocalDate.of(2026, 10, 5), 12, 10, RENT, PRICE);

		assertThat(plan).hasSize(13);
		Entry first = plan.getFirst();
		assertThat(first.periodStart()).isEqualTo("2026-10-05");
		assertThat(first.periodEnd()).isEqualTo("2026-10-10");
		assertThat(first.dueOn()).isEqualTo("2026-10-10");
		assertThat(first.monthShare()).isEqualByComparingTo("0.166667");
		assertThat(first.rent()).isEqualTo(Money.of("166.67", "INR"));
		assertThat(first.capital()).isEqualTo(Money.of("166.67", "INR"));

		Entry second = plan.get(1);
		assertThat(second.periodStart()).isEqualTo("2026-10-10");
		assertThat(second.dueOn()).isEqualTo("2026-11-10");
		assertThat(second.monthShare()).isEqualByComparingTo("1");
		assertThat(second.rent()).isEqualTo(RENT);

		// The rest of the tenure: 10 September to the lease end on 5 October, paid on 10 October.
		Entry last = plan.getLast();
		assertThat(last.periodStart()).isEqualTo("2027-09-10");
		assertThat(last.periodEnd()).isEqualTo("2027-10-05");
		assertThat(last.dueOn()).isEqualTo("2027-10-10");
		assertThat(last.monthShare()).isEqualByComparingTo("0.833333");
		assertThat(last.rent()).isEqualTo(Money.of("833.33", "INR"));
		assertThat(last.capital()).isEqualTo(Money.of("833.33", "INR"));
		assertWhole(plan, 12);
	}

	@Test
	void currentPlansScheduleRentOnly() {
		List<Entry> plan = PayoutSchedule.plan(LocalDate.of(2026, 10, 5), 12, 10, RENT, Money.zero(RENT.currency()));

		assertThat(plan).hasSize(13);
		assertThat(plan).allSatisfy(e -> assertThat(e.capital()).isEqualTo(Money.zero(RENT.currency())));
		assertThat(plan.stream().map(Entry::rent).reduce(Money.zero(RENT.currency()), Money::plus))
			.isEqualTo(Money.of("12000", "INR"));
	}

	@Test
	void aLeaseStartingOnThePayoutDayIsPaidWholeMonths() {
		List<Entry> plan = PayoutSchedule.plan(LocalDate.of(2026, 10, 10), 12, 10, RENT, PRICE);

		assertThat(plan).hasSize(12);
		assertThat(plan).allSatisfy(e -> {
			assertThat(e.monthShare()).isEqualByComparingTo("1");
			assertThat(e.rent()).isEqualTo(RENT);
			assertThat(e.capital()).isEqualTo(Money.of("1000", "INR"));
		});
		assertThat(plan.getFirst().dueOn()).isEqualTo("2026-11-10");
		assertThat(plan.getLast().dueOn()).isEqualTo("2027-10-10");
		assertWhole(plan, 12);
	}

	@Test
	void aLeaseStartingAfterThePayoutDayWaitsForNextMonths() {
		// Bought on 12 October: 29 of the 31 days from 10 October to 10 November.
		List<Entry> plan = PayoutSchedule.plan(LocalDate.of(2026, 10, 12), 12, 10, RENT, PRICE);

		assertThat(plan).hasSize(13);
		assertThat(plan.getFirst().dueOn()).isEqualTo("2026-11-10");
		assertThat(plan.getFirst().monthShare()).isEqualByComparingTo("0.935484");
		assertThat(plan.getLast().periodEnd()).isEqualTo("2027-10-12");
		assertThat(plan.getLast().dueOn()).isEqualTo("2027-11-10");
		assertThat(plan.getLast().monthShare()).isEqualByComparingTo("0.064516");
		assertWhole(plan, 12);
	}

	@Test
	void aPayoutDayPastTheEndOfAMonthFallsOnItsLastDay() {
		List<Entry> plan = PayoutSchedule.plan(LocalDate.of(2027, 1, 15), 3, 31, RENT, Money.of("3000", "INR"));

		assertThat(plan).extracting(Entry::dueOn).containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28),
				LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
		assertThat(plan.getFirst().monthShare()).isEqualByComparingTo("0.516129");
		assertThat(plan.get(1).monthShare()).isEqualByComparingTo("1");
		assertThat(plan.get(2).monthShare()).isEqualByComparingTo("1");
		assertWhole(plan, 3);
	}

	@Test
	void everyStartAndPayoutDayAddsUpToTheTenureExactly() {
		Money rent = Money.of("1234.57", "INR");
		Money price = Money.of("99999.99", "INR");
		for (int payoutDay = 1; payoutDay <= 31; payoutDay++) {
			for (LocalDate start = LocalDate.of(2027, 1, 1); start.getYear() == 2027; start = start.plusDays(1)) {
				for (int tenure : new int[] { 1, 7, 18 }) {
					List<Entry> plan = PayoutSchedule.plan(start, tenure, payoutDay, rent, price);
					assertThat(plan.size()).isBetween(tenure, tenure + 1);
					assertThat(plan.stream().map(Entry::rent).reduce(Money::plus).orElseThrow())
						.isEqualTo(rent.times(BigDecimal.valueOf(tenure)));
					assertThat(plan.stream().map(Entry::capital).reduce(Money::plus).orElseThrow()).isEqualTo(price);
					assertWhole(plan, tenure);
				}
			}
		}
	}

	/** Contiguous periods from the start, each paid on or after its end, with shares adding up to the tenure. */
	private static void assertWhole(List<Entry> plan, int tenure) {
		assertThat(plan.stream().map(Entry::monthShare).reduce(BigDecimal.ZERO, BigDecimal::add))
			.isEqualByComparingTo(BigDecimal.valueOf(tenure));
		for (int i = 0; i < plan.size(); i++) {
			Entry e = plan.get(i);
			assertThat(e.number()).isEqualTo(i + 1);
			assertThat(e.monthShare()).isPositive()
				.isLessThanOrEqualTo(i == plan.size() - 1 ? new BigDecimal("1.2") : BigDecimal.ONE);
			assertThat(e.periodStart()).isBefore(e.periodEnd());
			assertThat(e.dueOn()).isAfterOrEqualTo(e.periodEnd());
			assertThat(e.rent().isPositive() || e.rent().amount().signum() == 0).isTrue();
			assertThat(e.capital().amount().signum()).isNotNegative();
			if (i > 0) {
				assertThat(e.periodStart()).isEqualTo(plan.get(i - 1).periodEnd());
			}
		}
		assertThat(plan.getLast().periodEnd()).isEqualTo(plan.getFirst().periodStart().plusMonths(tenure));
	}

}
