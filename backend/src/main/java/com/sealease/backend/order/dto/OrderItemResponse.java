package com.sealease.backend.order.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One container of an order.
 *
 * @param containerNumber the container allocated to the investor; {@code null} until the order is paid
 *                        (staff always see the reserved container)
 */
public record OrderItemResponse(
		UUID id,
		UUID productId,
		String productCode,
		String productTitle,
		ContainerType containerType,
		String containerNumber,
		MoneyResponse amount,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		MoneyResponse monthlyPayout,
		int tenureMonths) {

	public static OrderItemResponse from(OrderItem i, String containerNumber) {
		Money monthly = ProductTerms.percentOf(i.amount(), i.getMonthlyRentPercent())
			.plus(ProductTerms.capitalPerMonth(i.amount(), i.getTenureMonths()));
		return new OrderItemResponse(i.getId(), i.getProductId(), i.getProductCode(), i.getProductTitle(),
				i.getContainerType(), containerNumber, MoneyResponse.from(i.amount()), i.getMonthlyRentPercent(),
				i.getMonthlyCapitalReturnPercent(), MoneyResponse.from(monthly), i.getTenureMonths());
	}

}
