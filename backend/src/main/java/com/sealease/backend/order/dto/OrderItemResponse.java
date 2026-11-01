package com.sealease.backend.order.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What an order bought.
 *
 * @param amount    what the investor invested (returns are based on it)
 * @param units     the units bought; null on orders placed before units
 * @param unitPrice what one unit cost; null on orders placed before units
 */
public record OrderItemResponse(
		UUID id,
		UUID productId,
		String productCode,
		String productTitle,
		MoneyResponse amount,
		Integer units,
		MoneyResponse unitPrice,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		MoneyResponse monthlyPayout,
		int tenureMonths) {

	public static OrderItemResponse from(OrderItem i) {
		Money monthly = ProductTerms.percentOf(i.amount(), i.getMonthlyRentPercent());
		if (ProductTerms.returnsCapital(i.getMonthlyCapitalReturnPercent())) {
			monthly = monthly.plus(ProductTerms.capitalPerMonth(i.amount(), i.getTenureMonths()));
		}
		MoneyResponse unitPrice = i.getUnitPrice() == null ? null
				: MoneyResponse.from(Money.of(i.getUnitPrice(), i.amount().currency()));
		return new OrderItemResponse(i.getId(), i.getProductId(), i.getProductCode(), i.getProductTitle(),
				MoneyResponse.from(i.amount()), i.getUnits(), unitPrice, i.getMonthlyRentPercent(),
				i.getMonthlyCapitalReturnPercent(), MoneyResponse.from(monthly), i.getTenureMonths());
	}

}
