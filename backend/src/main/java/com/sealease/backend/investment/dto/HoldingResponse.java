package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.investment.entity.Holding;
import com.sealease.backend.investment.entity.HoldingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One container the investor owns under a plan, with its lease and the payout it entitles to.
 * Payouts made so far are reported by the earnings module.
 */
public record HoldingResponse(
		UUID id,
		UUID productId,
		String productCode,
		String productTitle,
		ContainerSummary container,
		UUID orderId,
		MoneyResponse amount,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		MoneyResponse monthlyRent,
		MoneyResponse monthlyCapitalReturn,
		MoneyResponse monthlyPayout,
		int tenureMonths,
		MoneyResponse totalPayout,
		LocalDate leaseStartsOn,
		LocalDate leaseEndsOn,
		HoldingStatus status,
		Instant confirmedAt,
		Instant maturedAt) {

	public static HoldingResponse from(Holding h, OfferingTerms plan, ContainerSummary container) {
		Money monthly = h.monthlyRent().plus(h.monthlyCapitalReturn());
		return new HoldingResponse(h.getId(), h.getProductId(), plan.code(), plan.terms().title(), container,
				h.getOrderId(), MoneyResponse.from(h.amount()), h.getMonthlyRentPercent(),
				h.getMonthlyCapitalReturnPercent(), MoneyResponse.from(h.monthlyRent()),
				MoneyResponse.from(h.monthlyCapitalReturn()), MoneyResponse.from(monthly), h.getTenureMonths(),
				MoneyResponse.from(h.monthlyRent().times(BigDecimal.valueOf(h.getTenureMonths())).plus(h.amount())), h.getLeaseStartsOn(),
				h.getLeaseEndsOn(), h.getStatus(), h.getConfirmedAt(), h.getMaturedAt());
	}

}
