package com.sealease.backend.earning.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.UUID;

/**
 * The rent of one holding's lease, credited to the investor hour by hour from its start to its end.
 * <p>
 * The lease runs from {@code startsAt} (on the hour) for the tenure in whole months. What is owed up to an instant is
 * the monthly rent × the lease months elapsed by then, the current lease month counted by its hours (a lease month
 * runs from the start's day and hour in one month to the same in the next). Each credit is what is owed up to its end
 * less what has been credited, so the hours add up to exactly rent × tenure at the lease end, with no rounding drift.
 * Holdings sold before capital returns ended also get their capital back the same way, spread over the tenure.
 */
@Entity
@Table(name = "rent_accruals")
public class RentAccrual extends BaseEntity {

	/** Lease months are kept to twelve decimals: far below a paisa on any lease. */
	public static final int MONTH_SCALE = 12;

	@Column(name = "holding_id", nullable = false, updatable = false)
	private UUID holdingId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "monthly_rent", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal monthlyRent;

	@Column(name = "capital_total", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal capitalTotal;

	@Column(name = "tenure_months", nullable = false, updatable = false)
	private int tenureMonths;

	@Column(name = "starts_at", nullable = false, updatable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false, updatable = false)
	private Instant endsAt;

	/** Credited up to this instant (on the hour); {@code endsAt} once the whole lease has been. */
	@Column(name = "credited_until", nullable = false)
	private Instant creditedUntil;

	@Column(name = "credited_rent", nullable = false, precision = 19, scale = 4)
	private BigDecimal creditedRent = BigDecimal.ZERO;

	@Column(name = "credited_capital", nullable = false, precision = 19, scale = 4)
	private BigDecimal creditedCapital = BigDecimal.ZERO;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected RentAccrual() {
	}

	/**
	 * @param startsAt     the lease's first hour; must be on the hour
	 * @param capitalTotal returned over the tenure: the price under terms sold before capital returns ended, else zero
	 */
	public RentAccrual(UUID holdingId, UUID userId, UUID productId, Instant startsAt, int tenureMonths, Money monthlyRent,
			Money capitalTotal) {
		if (tenureMonths < 1) {
			throw new IllegalArgumentException("tenure " + tenureMonths);
		}
		if (!startsAt.truncatedTo(ChronoUnit.MINUTES).equals(startsAt)) {
			throw new IllegalArgumentException("A lease starts on a whole minute, not at " + startsAt);
		}
		if (!monthlyRent.currency().equals(capitalTotal.currency())) {
			throw new IllegalArgumentException("Rent and capital in different currencies");
		}
		this.holdingId = holdingId;
		this.userId = userId;
		this.productId = productId;
		this.currency = monthlyRent.currency().getCurrencyCode();
		this.monthlyRent = monthlyRent.amount();
		this.capitalTotal = capitalTotal.amount();
		this.tenureMonths = tenureMonths;
		this.startsAt = startsAt;
		this.endsAt = leaseEnd(startsAt, tenureMonths);
		this.creditedUntil = startsAt;
	}

	/** The end of a lease starting at {@code startsAt}: the same day and hour, the tenure in months later (UTC). */
	public static Instant leaseEnd(Instant startsAt, int tenureMonths) {
		return startsAt.atZone(ZoneOffset.UTC).plusMonths(tenureMonths).toInstant();
	}

	/**
	 * Lease months elapsed by {@code at}: whole lease months plus the hours of the current one ÷ its hours. 0 before
	 * the lease starts, the tenure from its end.
	 */
	public BigDecimal monthsAt(Instant at) {
		if (!at.isAfter(startsAt)) {
			return BigDecimal.ZERO;
		}
		if (!at.isBefore(endsAt)) {
			return BigDecimal.valueOf(tenureMonths);
		}
		ZonedDateTime start = startsAt.atZone(ZoneOffset.UTC);
		ZonedDateTime when = at.atZone(ZoneOffset.UTC);
		// Each lease month starts at start + k months, worked out from the start (not month after month).
		long whole = ChronoUnit.MONTHS.between(start, when);
		while (whole > 0 && start.plusMonths(whole).isAfter(when)) {
			whole--;
		}
		while (!start.plusMonths(whole + 1).isAfter(when)) {
			whole++;
		}
		Instant monthStart = start.plusMonths(whole).toInstant();
		Instant monthEnd = start.plusMonths(whole + 1).toInstant();
		BigDecimal part = BigDecimal.valueOf(ChronoUnit.SECONDS.between(monthStart, at))
			.divide(BigDecimal.valueOf(ChronoUnit.SECONDS.between(monthStart, monthEnd)), MONTH_SCALE, RoundingMode.HALF_UP);
		return BigDecimal.valueOf(whole).add(part);
	}

	/** Rent owed from the start up to {@code at}, to the currency's minor unit. */
	public Money rentUpTo(Instant at) {
		return owed(monthlyRent().times(BigDecimal.valueOf(tenureMonths)), at);
	}

	/** Capital owed back from the start up to {@code at}. */
	public Money capitalUpTo(Instant at) {
		return owed(capitalTotal(), at);
	}

	private Money owed(Money total, Instant at) {
		if (!at.isBefore(endsAt)) {
			return total;
		}
		int digits = Math.max(total.currency().getDefaultFractionDigits(), 0);
		BigDecimal share = monthsAt(at).divide(BigDecimal.valueOf(tenureMonths), MONTH_SCALE, RoundingMode.HALF_UP);
		return Money.of(total.amount().multiply(share).setScale(digits, RoundingMode.HALF_UP), total.currency());
	}

	/**
	 * Where crediting at {@code now} can get to: the start of the current credit step (the last whole step that has
	 * passed), within the lease.
	 */
	public Instant creditableUntil(Instant stepStart) {
		return stepStart.isAfter(endsAt) ? endsAt : stepStart;
	}

	/**
	 * What crediting up to {@code until} adds: rent and capital owed by then less what is already credited (never
	 * less than zero).
	 */
	public Money[] amountsUpTo(Instant until) {
		return new Money[] { atLeastZero(rentUpTo(until).minus(creditedRent())),
				atLeastZero(capitalUpTo(until).minus(creditedCapital())) };
	}

	private static Money atLeastZero(Money amount) {
		return amount.amount().signum() < 0 ? Money.zero(amount.currency()) : amount;
	}

	/** Credits the hours up to {@code until}; once the lease end is credited the accrual is complete. */
	public void creditUpTo(Instant until, Money rent, Money capital, Instant now) {
		if (completedAt != null) {
			throw new IllegalStateException("Rent of holding " + holdingId + " is already fully credited");
		}
		if (!until.isAfter(creditedUntil) || until.isAfter(endsAt)) {
			throw new IllegalArgumentException("Cannot credit holding " + holdingId + " up to " + until);
		}
		this.creditedRent = creditedRent.add(rent.amount());
		this.creditedCapital = creditedCapital.add(capital.amount());
		this.creditedUntil = until;
		if (creditedUntil.equals(endsAt)) {
			this.completedAt = now;
		}
	}

	public boolean isCompleted() {
		return completedAt != null;
	}

	public Money monthlyRent() {
		return Money.of(monthlyRent, Currency.getInstance(currency));
	}

	public Money totalRent() {
		return monthlyRent().times(BigDecimal.valueOf(tenureMonths));
	}

	public Money capitalTotal() {
		return Money.of(capitalTotal, Currency.getInstance(currency));
	}

	public Money creditedRent() {
		return Money.of(creditedRent, Currency.getInstance(currency));
	}

	public Money creditedCapital() {
		return Money.of(creditedCapital, Currency.getInstance(currency));
	}

	public Money creditedTotal() {
		return creditedRent().plus(creditedCapital());
	}

	public UUID getHoldingId() {
		return holdingId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getProductId() {
		return productId;
	}

	public int getTenureMonths() {
		return tenureMonths;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public Instant getCreditedUntil() {
		return creditedUntil;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Currency currency() {
		return Currency.getInstance(currency);
	}

}
