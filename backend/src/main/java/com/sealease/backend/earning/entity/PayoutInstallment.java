package com.sealease.backend.earning.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.UUID;

/**
 * One monthly payout of one holding: the month's rent plus the month's return of capital. Amounts
 * are fixed when the holding is created; paying it only records when and in which ledger
 * transaction the money was credited.
 */
@Entity
@Table(name = "payout_installments")
public class PayoutInstallment extends BaseEntity {

	@Column(name = "holding_id", nullable = false, updatable = false)
	private UUID holdingId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column(name = "installment_number", nullable = false, updatable = false)
	private int installmentNumber;

	@Column(name = "due_on", nullable = false, updatable = false)
	private LocalDate dueOn;

	@Column(name = "period_start", nullable = false, updatable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false, updatable = false)
	private LocalDate periodEnd;

	/** The period as a share of a month: 1 for a whole month, less for a part month. */
	@Column(name = "month_share", nullable = false, updatable = false, precision = 9, scale = 6)
	private BigDecimal monthShare;

	@Column(name = "installment_count", nullable = false, updatable = false)
	private int installmentCount;

	@Column(name = "rent_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal rentAmount;

	@Column(name = "capital_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal capitalAmount;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PayoutStatus status;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "ledger_transaction_id")
	private UUID ledgerTransactionId;

	/** The first day of the period not yet credited; {@code periodEnd} once all of it has been. */
	@Column(name = "credited_through", nullable = false)
	private LocalDate creditedThrough;

	@Column(name = "credited_rent", nullable = false, precision = 19, scale = 4)
	private BigDecimal creditedRent = BigDecimal.ZERO;

	@Column(name = "credited_capital", nullable = false, precision = 19, scale = 4)
	private BigDecimal creditedCapital = BigDecimal.ZERO;

	protected PayoutInstallment() {
	}

	public PayoutInstallment(UUID holdingId, UUID userId, UUID productId, int installmentNumber, int installmentCount,
			LocalDate periodStart, LocalDate periodEnd, BigDecimal monthShare, LocalDate dueOn, Money rent, Money capital) {
		this.holdingId = holdingId;
		this.userId = userId;
		this.productId = productId;
		this.installmentNumber = installmentNumber;
		this.installmentCount = installmentCount;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.monthShare = monthShare;
		this.dueOn = dueOn;
		this.rentAmount = rent.amount();
		this.capitalAmount = capital.amount();
		this.currency = rent.currency().getCurrencyCode();
		this.status = PayoutStatus.SCHEDULED;
		this.creditedThrough = periodStart;
	}

	/** Days in the period (its end is exclusive). */
	public int days() {
		return (int) ChronoUnit.DAYS.between(periodStart, periodEnd);
	}

	/**
	 * What one more day of the period credits: the total × days credited ÷ days in the period, less what is already
	 * credited, so the days add up to exactly the period's amount (the rounding is spread over them).
	 */
	public Money[] nextDay() {
		int credited = (int) ChronoUnit.DAYS.between(periodStart, creditedThrough) + 1;
		return new Money[] { share(rent(), credited).minus(creditedRent()), share(capital(), credited).minus(creditedCapital()) };
	}

	private Money share(Money total, int credited) {
		if (credited >= days()) {
			return total;
		}
		int digits = Math.max(total.currency().getDefaultFractionDigits(), 0);
		return Money.of(total.amount().multiply(BigDecimal.valueOf(credited))
			.divide(BigDecimal.valueOf(days()), digits, RoundingMode.HALF_UP), total.currency());
	}

	/** Credits the day {@link #getCreditedThrough()}; after the last day of the period the installment is PAID. */
	public void creditDay(Money rent, Money capital, UUID ledgerTransactionId, Instant now) {
		if (status != PayoutStatus.SCHEDULED) {
			throw new IllegalStateException("Payout " + getId() + " is already " + status);
		}
		this.creditedRent = creditedRent.add(rent.amount());
		this.creditedCapital = creditedCapital.add(capital.amount());
		this.creditedThrough = creditedThrough.plusDays(1);
		if (creditedThrough.equals(periodEnd)) {
			this.status = PayoutStatus.PAID;
			this.ledgerTransactionId = ledgerTransactionId;
			this.paidAt = now;
		}
	}

	public LocalDate getCreditedThrough() {
		return creditedThrough;
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

	public boolean isDueOn(LocalDate day) {
		return !dueOn.isAfter(day);
	}

	public Money rent() {
		return Money.of(rentAmount, Currency.getInstance(currency));
	}

	public Money capital() {
		return Money.of(capitalAmount, Currency.getInstance(currency));
	}

	public Money total() {
		return rent().plus(capital());
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

	public int getInstallmentNumber() {
		return installmentNumber;
	}

	public int getInstallmentCount() {
		return installmentCount;
	}

	public LocalDate getPeriodStart() {
		return periodStart;
	}

	public LocalDate getPeriodEnd() {
		return periodEnd;
	}

	public BigDecimal getMonthShare() {
		return monthShare;
	}

	public LocalDate getDueOn() {
		return dueOn;
	}

	public PayoutStatus getStatus() {
		return status;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public UUID getLedgerTransactionId() {
		return ledgerTransactionId;
	}

}
