package com.sealease.backend.order.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.RentalFrequency;
import com.sealease.backend.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
		UUID productId,
		String productCode,
		String productTitle,
		InvestmentType investmentType,
		MoneyResponse amount,
		BigDecimal ownershipPercent,
		MoneyResponse rentalPerPayment,
		RentalFrequency rentalFrequency,
		int durationMonths,
		String termsVersion) {

	public static OrderItemResponse from(OrderItem i) {
		return new OrderItemResponse(i.getProductId(), i.getProductCode(), i.getProductTitle(), i.getInvestmentType(),
				MoneyResponse.from(i.amount()), i.getOwnershipPercent(), MoneyResponse.from(i.rentalPerPayment()),
				i.getRentalFrequency(), i.getDurationMonths(), i.getTermsVersion());
	}

}
