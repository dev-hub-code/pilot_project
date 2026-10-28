package com.sealease.backend.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.entity.ProductTerms;

/**
 * A demo account's figures, worked out from its dummy orders and downline over its time frame; nothing is stored.
 *
 * <ul>
 * <li>Rent: each of the investor's own orders earns its monthly rent (price × containers × rent %) per lease month, a
 * lease month running from the start's day in one month to the same day in the next, and a part month counted by its
 * days. So a whole lease pays exactly monthly rent × tenure.</li>
 * <li>Commission: each downline member's order earns the investor that level's rate (levels 1-4) of the member's
 * investment per lease month, the same way. Deeper levels earn nothing.</li>
 * <li>Payouts: a calendar month's rent is withdrawn on the 1st of the next month, its commission on the 5th, once that
 * day has come.</li>
 * </ul>
 *
 * Earnings are credited from the time frame's start up to today (or the frame's end, if sooner); what the frame still
 * holds after today is "scheduled". Dates are in the business's time zone.
 */
public final class DemoBook {

	/** The 1st of the month after the rent was earned. */
	public static final int RENT_PAYOUT_DAY = 1;

	/** The 5th of the month after the commission was earned. */
	public static final int REFERRAL_PAYOUT_DAY = 5;

	/** Commission is paid on levels 1 to 4. */
	public static final int COMMISSION_LEVELS = 4;

	/**
	 * @param memberId {@code null} for the demo investor's own order
	 */
	public record Order(UUID id, UUID memberId, String number, UUID productId, String planCode, String planTitle,
			ContainerType containerType,
			int containers, Money pricePerContainer, BigDecimal rentPercent, int tenureMonths, LocalDate startsOn) {

		public Money amount() {
			return pricePerContainer.times(BigDecimal.valueOf(containers));
		}

		public Money monthlyRent() {
			return ProductTerms.percentOf(amount(), rentPercent);
		}

		public Money monthlyRentPerContainer() {
			return ProductTerms.percentOf(pricePerContainer, rentPercent);
		}

		public LocalDate endsOn() {
			return startsOn.plusMonths(tenureMonths);
		}

	}

	/** @param level 1 for a member referred by the demo investor directly */
	public record Member(UUID id, UUID parentId, String name, LocalDate joinedOn, int level) {
	}

	public enum Kind {
		RENT, REFERRAL
	}

	/** A payout made on {@code paidOn} of what was earned in {@code earnedIn}. */
	public record Payout(Kind kind, YearMonth earnedIn, LocalDate paidOn, Money amount) {
	}

	/**
	 * One calendar month.
	 *
	 * @param commission paid per level 1-4, in order
	 */
	public record Month(YearMonth month, Money rentPaid, Money rentScheduled, List<Money> commission,
			List<Money> commissionScheduled) {

		public Money commissionTotal() {
			return commission.stream().reduce(Money::plus).orElseThrow();
		}

	}

	private final LocalDate startsOn;
	private final LocalDate endsOn;
	private final LocalDate today;
	private final Currency currency;
	private final List<Order> own = new ArrayList<>();
	private final List<Order> downline = new ArrayList<>();
	private final Map<UUID, Member> members = new LinkedHashMap<>();
	private final List<BigDecimal> rates;
	private final List<Month> months = new ArrayList<>();
	private final List<Payout> payouts = new ArrayList<>();
	private Money rentEarned;
	private Money commissionEarned;

	/**
	 * @param rates commission percent per level, level 1 first
	 */
	public DemoBook(LocalDate startsOn, LocalDate endsOn, LocalDate today, Currency currency, List<Order> orders,
			List<Member> members, List<BigDecimal> rates) {
		this.startsOn = startsOn;
		this.endsOn = endsOn;
		this.today = today;
		this.currency = currency;
		this.rentEarned = Money.zero(currency);
		this.commissionEarned = Money.zero(currency);
		this.rates = List.copyOf(rates);
		members.forEach(m -> this.members.put(m.id(), m));
		for (Order order : orders) {
			(order.memberId() == null ? own : downline).add(order);
		}
		work();
	}

	private void work() {
		LocalDate credited = creditedUntil();
		LocalDate frameEnd = endsOn.plusDays(1);
		for (YearMonth m = YearMonth.from(startsOn); !m.isAfter(YearMonth.from(endsOn)); m = m.plusMonths(1)) {
			LocalDate from = max(m.atDay(1), startsOn);
			LocalDate to = min(m.plusMonths(1).atDay(1), frameEnd);
			LocalDate paidTo = min(to, credited);
			LocalDate scheduledFrom = max(from, credited);
			Money rentPaid = zero();
			Money rentScheduled = zero();
			for (Order order : own) {
				rentPaid = rentPaid.plus(earned(order.monthlyRent(), order, from, paidTo));
				rentScheduled = rentScheduled.plus(earned(order.monthlyRent(), order, scheduledFrom, to));
			}
			List<Money> commission = new ArrayList<>(Collections.nCopies(COMMISSION_LEVELS, zero()));
			List<Money> commissionScheduled = new ArrayList<>(Collections.nCopies(COMMISSION_LEVELS, zero()));
			for (Order order : downline) {
				int level = levelOf(order);
				if (level < 1 || level > COMMISSION_LEVELS) {
					continue;
				}
				Money base = monthlyCommission(order, level);
				commission.set(level - 1, commission.get(level - 1).plus(earned(base, order, from, paidTo)));
				commissionScheduled.set(level - 1,
						commissionScheduled.get(level - 1).plus(earned(base, order, scheduledFrom, to)));
			}
			Month month = new Month(m, rentPaid, rentScheduled, List.copyOf(commission), List.copyOf(commissionScheduled));
			months.add(month);
			pay(Kind.RENT, m, m.plusMonths(1).atDay(RENT_PAYOUT_DAY), rentPaid);
			pay(Kind.REFERRAL, m, m.plusMonths(1).atDay(REFERRAL_PAYOUT_DAY), month.commissionTotal());
		}
	}

	/** Pays what was earned so far, in whole minor units: a part paisa is carried into the next payout. */
	private void pay(Kind kind, YearMonth earnedIn, LocalDate paidOn, Money earned) {
		Money soFar = (kind == Kind.RENT ? rentEarned : commissionEarned).plus(earned);
		if (kind == Kind.RENT) {
			rentEarned = soFar;
		} else {
			commissionEarned = soFar;
		}
		Money amount = whole(soFar).minus(withdrawn(kind));
		if (!paidOn.isAfter(today) && amount.isPositive()) {
			payouts.add(new Payout(kind, earnedIn, paidOn, amount));
		}
	}

	// ----------------------------------------------------------------------------- results

	/** Earnings are credited up to (not including) this day: tomorrow, or the day after the frame ends if sooner. */
	public LocalDate creditedUntil() {
		return min(today.plusDays(1), endsOn.plusDays(1));
	}

	public LocalDate startsOn() {
		return startsOn;
	}

	public LocalDate endsOn() {
		return endsOn;
	}

	public LocalDate today() {
		return today;
	}

	public Currency currency() {
		return currency;
	}

	public List<Order> ownOrders() {
		return List.copyOf(own);
	}

	public List<Order> downlineOrders() {
		return List.copyOf(downline);
	}

	public List<Member> members() {
		return List.copyOf(members.values());
	}

	/** Every calendar month of the time frame, oldest first. */
	public List<Month> months() {
		return List.copyOf(months);
	}

	/** Payouts made so far, oldest first. */
	public List<Payout> payouts() {
		List<Payout> sorted = new ArrayList<>(payouts);
		sorted.sort((a, b) -> a.paidOn().compareTo(b.paidOn()));
		return sorted;
	}

	public List<BigDecimal> rates() {
		return rates;
	}

	public Money rentPaid() {
		return months.stream().map(Month::rentPaid).reduce(zero(), Money::plus);
	}

	public Money commissionPaid() {
		return months.stream().map(Month::commissionTotal).reduce(zero(), Money::plus);
	}

	public Money withdrawn(Kind kind) {
		return payouts.stream().filter(p -> p.kind() == kind).map(Payout::amount).reduce(zero(), Money::plus);
	}

	/** Credited and not yet withdrawn. */
	public Money balance(Kind kind) {
		return (kind == Kind.RENT ? rentPaid() : commissionPaid()).minus(withdrawn(kind));
	}

	/** What the order's lease has credited so far (rent, per the whole order). */
	public Money rentCredited(Order order) {
		return earned(order.monthlyRent(), order, startsOn, creditedUntil());
	}

	/** Commission credited so far on the member's orders. */
	public Money commissionFrom(UUID memberId) {
		Money total = zero();
		for (Order order : downline) {
			int level = levelOf(order);
			if (memberId.equals(order.memberId()) && level >= 1 && level <= COMMISSION_LEVELS) {
				total = total.plus(earned(monthlyCommission(order, level), order, startsOn, creditedUntil()));
			}
		}
		return total;
	}

	/** Members at each level 1-4. */
	public long membersAt(int level) {
		return members.values().stream().filter(m -> m.level() == level).count();
	}

	// ----------------------------------------------------------------------------- arithmetic

	private int levelOf(Order order) {
		Member member = members.get(order.memberId());
		return member == null ? 0 : member.level();
	}

	private Money monthlyCommission(Order order, int level) {
		BigDecimal rate = level <= rates.size() ? rates.get(level - 1) : BigDecimal.ZERO;
		return ProductTerms.percentOf(order.amount(), rate);
	}

	/**
	 * {@code monthly} × the order's lease months between the two days (none outside the lease): the difference of what
	 * is owed by each day, so consecutive periods add up exactly to what is owed over both.
	 */
	public static Money earned(Money monthly, Order order, LocalDate from, LocalDate to) {
		if (!to.isAfter(from)) {
			return Money.zero(monthly.currency());
		}
		return monthly.times(monthsAt(order.startsOn(), order.tenureMonths(), to))
			.minus(monthly.times(monthsAt(order.startsOn(), order.tenureMonths(), from)));
	}

	/**
	 * Lease months elapsed at the start of {@code at}: whole months, plus the current lease month's fraction by days.
	 * Zero before the start, the tenure from the end.
	 */
	static BigDecimal monthsAt(LocalDate start, int tenureMonths, LocalDate at) {
		if (!at.isAfter(start)) {
			return BigDecimal.ZERO;
		}
		if (!at.isBefore(start.plusMonths(tenureMonths))) {
			return BigDecimal.valueOf(tenureMonths);
		}
		long whole = ChronoUnit.MONTHS.between(start, at);
		while (start.plusMonths(whole + 1).compareTo(at) <= 0) {
			whole++;
		}
		while (start.plusMonths(whole).isAfter(at)) {
			whole--;
		}
		LocalDate from = start.plusMonths(whole);
		long days = ChronoUnit.DAYS.between(from, start.plusMonths(whole + 1));
		BigDecimal fraction = BigDecimal.valueOf(ChronoUnit.DAYS.between(from, at))
			.divide(BigDecimal.valueOf(days), 12, RoundingMode.HALF_UP);
		return BigDecimal.valueOf(whole).add(fraction);
	}

	/** Whole minor units, rounded down: what a payout can be. */
	private static Money whole(Money amount) {
		int digits = Math.max(amount.currency().getDefaultFractionDigits(), 0);
		return Money.of(amount.amount().setScale(digits, RoundingMode.DOWN), amount.currency());
	}

	private Money zero() {
		return Money.zero(currency);
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

	private static LocalDate max(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

}
