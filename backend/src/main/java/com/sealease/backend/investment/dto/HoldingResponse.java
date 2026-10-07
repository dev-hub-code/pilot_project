package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.entity.Holding;
import com.sealease.backend.investment.entity.HoldingStatus;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.entity.RentalFrequency;
import com.sealease.backend.investment.service.InvestmentAmountPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One confirmed investment with the offering it is in and the rental share it entitles to. */
public record HoldingResponse(
		UUID id,
		UUID productId,
		String productCode,
		String productTitle,
		InvestmentType investmentType,
		ProductStatus productStatus,
		UUID orderId,
		MoneyResponse amount,
		BigDecimal ownershipPercent,
		MoneyResponse expectedRentalPerPayment,
		RentalFrequency rentalFrequency,
		int durationMonths,
		String termsVersion,
		HoldingStatus status,
		Instant confirmedAt) {

	public static HoldingResponse from(Holding h, OfferingTerms offering) {
		ProductTerms t = offering.terms();
		Money amount = h.amount();
		return new HoldingResponse(h.getId(), h.getProductId(), offering.code(), t.title(), t.investmentType(),
				offering.status(), h.getOrderId(), MoneyResponse.from(amount), h.getOwnershipPercent(),
				MoneyResponse.from(InvestmentAmountPolicy.rentalShare(t, amount)), t.rentalFrequency(),
				t.durationMonths(), h.getTermsVersion(), h.getStatus(), h.getConfirmedAt());
	}

}
