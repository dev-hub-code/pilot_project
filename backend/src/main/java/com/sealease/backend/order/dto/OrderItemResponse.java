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
 * @param amount          the container's price, without GST (rent is based on it)
 * @param amountWithGst   what the investor paid for it: the price plus GST
 * @param containerNumber the container assigned to the investor; {@code null} until finance assigns one after
 *                        payment (staff also see containers reserved by orders placed before manual assignment)
 */
public record OrderItemResponse(
		UUID id,
		UUID productId,
		String productCode,
		String productTitle,
		ContainerType containerType,
		String containerNumber,
		MoneyResponse amount,
		BigDecimal gstRatePercent,
		MoneyResponse gstAmount,
		MoneyResponse amountWithGst,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		MoneyResponse monthlyPayout,
		int tenureMonths) {

	public static OrderItemResponse from(OrderItem i, String containerNumber) {
		Money monthly = ProductTerms.percentOf(i.amount(), i.getMonthlyRentPercent());
		if (ProductTerms.returnsCapital(i.getMonthlyCapitalReturnPercent())) {
			monthly = monthly.plus(ProductTerms.capitalPerMonth(i.amount(), i.getTenureMonths()));
		}
		return new OrderItemResponse(i.getId(), i.getProductId(), i.getProductCode(), i.getProductTitle(),
				i.getContainerType(), containerNumber, MoneyResponse.from(i.amount()), i.getGstRatePercent(),
				MoneyResponse.from(i.gst()), MoneyResponse.from(i.amountWithGst()), i.getMonthlyRentPercent(),
				i.getMonthlyCapitalReturnPercent(), MoneyResponse.from(monthly), i.getTenureMonths());
	}

}
