package com.sealease.backend.earning.entity;

import com.sealease.backend.common.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutInstallmentTest {

	private static final Money ZERO = Money.of("0", "INR");

	@Test
	void aMonthIsCreditedDayByDayAndAddsUpToExactlyItsRent() {
		// October: 31 days of ₹1,000 a month, ₹32.258… a day.
		PayoutInstallment october = installment(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), "1000", "0");
		Money total = ZERO;
		for (int day = 1; day <= 31; day++) {
			Money[] next = october.nextDay();
			assertThat(next[0].amount()).isBetween(new BigDecimal("32.25"), new BigDecimal("32.27"));
			assertThat(next[1].isZero()).isTrue();
			total = total.plus(next[0]);
			october.creditDay(next[0], next[1], UUID.randomUUID(), Instant.EPOCH);
			assertThat(october.getStatus()).isEqualTo(day < 31 ? PayoutStatus.SCHEDULED : PayoutStatus.PAID);
		}
		assertThat(total).isEqualTo(Money.of("1000.00", "INR"));
		assertThat(october.creditedRent()).isEqualTo(Money.of("1000.00", "INR"));
		assertThat(october.getCreditedThrough()).isEqualTo(LocalDate.of(2026, 11, 1));
	}

	@Test
	void capitalOnOlderHoldingsIsSpreadOverTheDaysToo() {
		// February 2027 has 28 days.
		PayoutInstallment february = installment(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), "1000", "3125.50");
		Money rent = ZERO;
		Money capital = ZERO;
		while (february.getStatus() == PayoutStatus.SCHEDULED) {
			Money[] next = february.nextDay();
			rent = rent.plus(next[0]);
			capital = capital.plus(next[1]);
			february.creditDay(next[0], next[1], UUID.randomUUID(), Instant.EPOCH);
		}
		assertThat(rent).isEqualTo(Money.of("1000.00", "INR"));
		assertThat(capital).isEqualTo(Money.of("3125.50", "INR"));
		assertThat(february.days()).isEqualTo(28);
	}

	private static PayoutInstallment installment(LocalDate start, LocalDate end, String rent, String capital) {
		return new PayoutInstallment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1, start, end,
				BigDecimal.ONE, end, Money.of(rent, "INR"), Money.of(capital, "INR"));
	}

}
