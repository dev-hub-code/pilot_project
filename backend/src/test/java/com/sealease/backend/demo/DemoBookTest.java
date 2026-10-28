package com.sealease.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.demo.service.DemoBook;
import com.sealease.backend.demo.service.DemoBook.Kind;
import com.sealease.backend.demo.service.DemoBook.Payout;

/** A demo account's figures, worked out by hand. */
class DemoBookTest {

	private static final Currency INR = Currency.getInstance("INR");
	private static final List<BigDecimal> RATES = List.of(new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("0.5"),
			new BigDecimal("0.25"));

	private static final UUID ASHA = UUID.randomUUID();
	private static final UUID BO = UUID.randomUUID();
	private static final UUID DEEP = UUID.randomUUID();

	/**
	 * Frame January to June 2026, today 10 April. The investor's own order: 2 containers of ₹50,000 at 2% (₹2,000 a
	 * month) from 1 January. Asha (level 1) invests ₹1,00,000 from 1 February: 2% = ₹2,000 a month. Bo (level 2)
	 * invests ₹50,000 from 15 February: 1% = ₹500 a lease month. Deep is five levels down and earns nothing.
	 */
	private static DemoBook book(LocalDate today) {
		List<DemoBook.Member> members = List.of(
				new DemoBook.Member(ASHA, null, "Asha", LocalDate.of(2026, 1, 15), 1),
				new DemoBook.Member(BO, ASHA, "Bo", LocalDate.of(2026, 2, 1), 2),
				new DemoBook.Member(DEEP, BO, "Deep", LocalDate.of(2026, 2, 1), 5));
		List<DemoBook.Order> orders = List.of(
				order(null, 2, "50000", LocalDate.of(2026, 1, 1)),
				order(ASHA, 1, "100000", LocalDate.of(2026, 2, 1)),
				order(BO, 1, "50000", LocalDate.of(2026, 2, 15)),
				order(DEEP, 1, "100000", LocalDate.of(2026, 2, 1)));
		return new DemoBook(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), today, INR, orders, members, RATES);
	}

	private static DemoBook.Order order(UUID member, int containers, String price, LocalDate startsOn) {
		return new DemoBook.Order(UUID.randomUUID(), member, "ORD-1", UUID.randomUUID(), "LST-1", "Plan", ContainerType.DRY_20FT, containers,
				Money.of(new BigDecimal(price), INR), new BigDecimal("2"), 12, startsOn);
	}

	@Test
	void rentIsPaidOnTheFirstAndCommissionOnTheFifthOfTheNextMonth() {
		DemoBook book = book(LocalDate.of(2026, 4, 10));

		assertThat(book.payouts()).containsExactly(
				new Payout(Kind.RENT, YearMonth.of(2026, 1), LocalDate.of(2026, 2, 1), inr("2000.00")),
				new Payout(Kind.RENT, YearMonth.of(2026, 2), LocalDate.of(2026, 3, 1), inr("2000.00")),
				// February: Asha ₹2,000 + Bo half a lease month (14 of 28 days) ₹250.
				new Payout(Kind.REFERRAL, YearMonth.of(2026, 2), LocalDate.of(2026, 3, 5), inr("2250.00")),
				new Payout(Kind.RENT, YearMonth.of(2026, 3), LocalDate.of(2026, 4, 1), inr("2000.00")),
				// March: Asha ₹2,000 + Bo ₹250 (to the 15th) + 17/31 of ₹500 = ₹274.19.
				new Payout(Kind.REFERRAL, YearMonth.of(2026, 3), LocalDate.of(2026, 4, 5), inr("2524.19")));
	}

	@Test
	void earningsAreCreditedToTodayAndTheRestOfTheFrameIsScheduled() {
		DemoBook book = book(LocalDate.of(2026, 4, 10));
		DemoBook.Month april = book.months().get(3);

		// Credited through 10 April: 10 of April's 30 days.
		assertThat(round(april.rentPaid())).isEqualTo(inr("666.67"));
		assertThat(round(april.rentScheduled())).isEqualTo(inr("1333.33"));
		assertThat(book.months().get(4).rentPaid().isZero()).isTrue();
		assertThat(round(book.months().get(4).rentScheduled())).isEqualTo(inr("2000.00"));
		assertThat(book.months()).hasSize(6);

		assertThat(round(book.rentPaid())).isEqualTo(inr("6666.67"));
		assertThat(book.withdrawn(Kind.RENT)).isEqualTo(inr("6000.00"));
		assertThat(round(book.balance(Kind.RENT))).isEqualTo(inr("666.67"));

		// April so far: Asha 10/30 of ₹2,000 + Bo 10/31 of ₹500 = ₹666.67 + ₹161.29.
		assertThat(round(april.commissionTotal())).isEqualTo(inr("827.96"));
		assertThat(round(book.commissionPaid())).isEqualTo(inr("5602.15"));
		assertThat(round(book.balance(Kind.REFERRAL))).isEqualTo(inr("827.96"));

		// By level; level 5 earns nothing.
		assertThat(april.commission().get(0)).isEqualTo(book.months().get(3).commission().get(0));
		assertThat(round(book.commissionFrom(ASHA))).isEqualTo(inr("4666.67"));
		assertThat(book.commissionFrom(DEEP).isZero()).isTrue();
		assertThat(book.membersAt(1)).isEqualTo(1);
		assertThat(book.membersAt(2)).isEqualTo(1);
	}

	@Test
	void nothingIsCreditedAfterTheFrameEnds() {
		DemoBook book = book(LocalDate.of(2026, 12, 1));

		assertThat(book.creditedUntil()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(round(book.rentPaid())).isEqualTo(inr("12000.00"));
		assertThat(book.months()).allSatisfy(m -> assertThat(m.rentScheduled().isZero()).isTrue());
		// June's rent and commission are still paid out, on 1 and 5 July.
		assertThat(book.payouts()).filteredOn(p -> p.earnedIn().equals(YearMonth.of(2026, 6))).hasSize(2);
		assertThat(book.balance(Kind.RENT).isZero()).isTrue();
	}

	@Test
	void aWholeLeasePaysExactlyRentTimesTenure() {
		// From the 31st: lease months end on the last day of shorter months.
		DemoBook.Order order = new DemoBook.Order(UUID.randomUUID(), null, "ORD-2", UUID.randomUUID(), "LST-1", "Plan", ContainerType.DRY_20FT, 1,
				inr("50000"), new BigDecimal("2"), 3, LocalDate.of(2026, 1, 31));
		DemoBook book = new DemoBook(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31), INR,
				List.of(order), List.of(), RATES);

		assertThat(book.rentPaid()).isEqualTo(inr("3000.0000"));
		assertThat(book.payouts().stream().map(Payout::amount).reduce(Money::plus).orElseThrow()).isEqualTo(inr("3000.00"));
		assertThat(book.balance(Kind.RENT).isZero()).isTrue();
	}

	private static Money inr(String amount) {
		return Money.of(new BigDecimal(amount), INR);
	}

	private static Money round(Money amount) {
		return Money.of(amount.toMinorUnitScale(), INR);
	}

}
