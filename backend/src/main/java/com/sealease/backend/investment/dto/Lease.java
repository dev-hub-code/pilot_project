package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.entity.RentalFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

/**
 * An offering on lease, as the earnings module sees it, with its rental schedule. Periods are
 * numbered from 1; period {@code n} runs from {@code startsOn + (n − 1) × months per period} up to,
 * but not including, the next period's start. Rent is due when a period has ended (in arrears).
 */
public record Lease(
		UUID productId,
		String code,
		String title,
		ProductStatus status,
		Money price,
		Money expectedRental,
		RentalFrequency rentalFrequency,
		int durationMonths,
		BigDecimal managementFeePercent,
		LocalDate startsOn,
		LocalDate endsOn) {

	public static Lease of(InvestmentProduct p) {
		ProductTerms t = p.terms();
		Currency currency = t.currency();
		return new Lease(p.getId(), p.getCode(), t.title(), p.getStatus(), Money.of(t.totalAmount(), currency),
				Money.of(t.expectedRentalAmount(), currency), t.rentalFrequency(), t.durationMonths(),
				t.managementFeePercent(), p.getLeaseStartsOn(), p.getLeaseEndsOn());
	}

	/** Rental periods within the term; a trailing part-period shorter than the frequency is not one. */
	public static int periodCount(RentalFrequency frequency, int durationMonths) {
		return durationMonths / frequency.monthsPerPeriod();
	}

	public int periodCount() {
		return periodCount(rentalFrequency, durationMonths);
	}

	public Currency currency() {
		return price.currency();
	}

	public Optional<RentalPeriod> period(int number) {
		if (startsOn == null || number < 1 || number > periodCount()) {
			return Optional.empty();
		}
		int months = rentalFrequency.monthsPerPeriod();
		LocalDate from = startsOn.plusMonths((long) (number - 1) * months);
		return Optional.of(new RentalPeriod(number, from, startsOn.plusMonths((long) number * months)));
	}

	/**
	 * @param endsOn exclusive: the first day of the next period
	 */
	public record RentalPeriod(int number, LocalDate startsOn, LocalDate endsOn) {

		public boolean isDueOn(LocalDate day) {
			return !endsOn.isAfter(day);
		}

	}

}
