package com.sealease.backend.earning.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.entity.ProductTerms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A holding's payouts when every payout is made on the platform's payout day.
 * <p>
 * The lease runs from its start for the tenure in whole months. Payout days split it into periods: a
 * lease that starts between payout days begins with a part month (start to the next payout day) and
 * ends with one (the last payout day to the lease end, paid on the payout day after it). Each period
 * is paid its share of a month: actual days ÷ days from the previous payout day to its own. The last
 * period takes whatever is left of the tenure, so the shares add up to exactly the tenure and rent and
 * capital to exactly their totals. (For a lease starting on the 29th-31st, month arithmetic can leave
 * the last period slightly more than a month: 31 January + 1 month is 28 February.)
 */
public final class PayoutSchedule {

	/** Shares of a month are kept to six decimals. */
	static final int SHARE_SCALE = 6;
	/** The last period's share bound (see the class comment). */
	private static final BigDecimal MAX_LAST_SHARE = new BigDecimal("1.2");

	/**
	 * One payout.
	 *
	 * @param monthShare the period as a share of a month: 1 for a whole month (the last can exceed 1 slightly)
	 */
	public record Entry(int number, LocalDate periodStart, LocalDate periodEnd, LocalDate dueOn, BigDecimal monthShare,
			Money rent, Money capital) {
	}

	private PayoutSchedule() {
	}

	/**
	 * @param payoutDay  1-31; past a month's last day, the payout is made on its last day
	 * @param monthlyRent rent for a whole month
	 * @param capitalToReturn returned in full over the tenure: the price under terms sold before capital returns
	 *                    ended, otherwise zero (the installments are then rent only)
	 */
	public static List<Entry> plan(LocalDate leaseStart, int tenureMonths, int payoutDay, Money monthlyRent, Money capitalToReturn) {
		if (tenureMonths < 1 || payoutDay < 1 || payoutDay > 31) {
			throw new IllegalArgumentException("tenure " + tenureMonths + ", payout day " + payoutDay);
		}
		LocalDate leaseEnd = leaseStart.plusMonths(tenureMonths);

		// Period boundaries: the start, every payout day inside the lease, the end.
		List<LocalDate> bounds = new ArrayList<>();
		bounds.add(leaseStart);
		for (LocalDate day = after(leaseStart, payoutDay); day.isBefore(leaseEnd); day = after(day, payoutDay)) {
			bounds.add(day);
		}
		bounds.add(leaseEnd);

		int count = bounds.size() - 1;
		Money capitalPerMonth = ProductTerms.capitalPerMonth(capitalToReturn, tenureMonths);
		Money rentLeft = monthlyRent.times(BigDecimal.valueOf(tenureMonths));
		Money capitalLeft = capitalToReturn;
		BigDecimal shareLeft = BigDecimal.valueOf(tenureMonths);
		List<Entry> entries = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			LocalDate from = bounds.get(i);
			LocalDate to = bounds.get(i + 1);
			boolean last = i == count - 1;
			BigDecimal share;
			Money rent;
			Money capital;
			if (last) {
				share = shareLeft;
				rent = rentLeft;
				capital = capitalLeft;
			}
			else {
				share = share(from, to, payoutDay);
				rent = prorate(monthlyRent, share);
				capital = prorate(capitalPerMonth, share);
			}
			if (share.signum() <= 0 || share.compareTo(last ? MAX_LAST_SHARE : BigDecimal.ONE) > 0) {
				throw new IllegalStateException("Period " + from + "–" + to + " has a share of " + share);
			}
			// The last period ends with the lease; it is paid on the payout day on or after its end.
			LocalDate dueOn = last ? onOrAfter(to, payoutDay) : to;
			entries.add(new Entry(i + 1, from, to, dueOn, share, rent, capital));
			shareLeft = shareLeft.subtract(share);
			rentLeft = rentLeft.minus(rent);
			capitalLeft = capitalLeft.minus(capital);
		}
		return entries;
	}

	/** The payout day of a month: {@code payoutDay}, or the month's last day if it is shorter. */
	public static LocalDate payoutDate(YearMonth month, int payoutDay) {
		return month.atDay(Math.min(payoutDay, month.lengthOfMonth()));
	}

	/** The first payout day strictly after {@code day}. */
	static LocalDate after(LocalDate day, int payoutDay) {
		LocalDate same = payoutDate(YearMonth.from(day), payoutDay);
		return same.isAfter(day) ? same : payoutDate(YearMonth.from(day).plusMonths(1), payoutDay);
	}

	/** The first payout day on or after {@code day}. */
	static LocalDate onOrAfter(LocalDate day, int payoutDay) {
		LocalDate same = payoutDate(YearMonth.from(day), payoutDay);
		return same.isBefore(day) ? payoutDate(YearMonth.from(day).plusMonths(1), payoutDay) : same;
	}

	/** Days from {@code from} to the payout day {@code to}, over the days from the payout day before it. */
	private static BigDecimal share(LocalDate from, LocalDate to, int payoutDay) {
		LocalDate previous = payoutDate(YearMonth.from(to).minusMonths(1), payoutDay);
		if (!from.isAfter(previous)) {
			return BigDecimal.ONE;
		}
		long days = ChronoUnit.DAYS.between(from, to);
		long month = ChronoUnit.DAYS.between(previous, to);
		return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(month), SHARE_SCALE, RoundingMode.HALF_UP);
	}

	/** A whole month's amount × share, rounded half-up to the currency's minor unit. */
	private static Money prorate(Money monthly, BigDecimal share) {
		if (share.compareTo(BigDecimal.ONE) == 0) {
			return monthly;
		}
		int digits = Math.max(monthly.currency().getDefaultFractionDigits(), 0);
		return Money.of(monthly.amount().multiply(share).setScale(digits, RoundingMode.HALF_UP), monthly.currency());
	}

}
