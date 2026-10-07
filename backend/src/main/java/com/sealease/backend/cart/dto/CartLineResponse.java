package com.sealease.backend.cart.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.RentalFrequency;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @param problems why this line cannot be checked out right now (empty when it can)
 */
public record CartLineResponse(
		UUID productId,
		String productCode,
		String productTitle,
		InvestmentType investmentType,
		ProductStatus productStatus,
		MoneyResponse amount,
		BigDecimal ownershipPercent,
		MoneyResponse rentalPerPayment,
		RentalFrequency rentalFrequency,
		int durationMonths,
		String termsVersion,
		List<String> problems) {
}
