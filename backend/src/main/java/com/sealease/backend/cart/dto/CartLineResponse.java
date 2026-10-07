package com.sealease.backend.cart.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Containers of one plan in the cart. Payout figures cover all {@code quantity} containers.
 *
 * @param problems why this line cannot be checked out right now (empty when it can)
 */
public record CartLineResponse(
		UUID productId,
		String productCode,
		String productTitle,
		ContainerType containerType,
		ProductStatus productStatus,
		int quantity,
		MoneyResponse pricePerContainer,
		MoneyResponse amount,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		MoneyResponse monthlyPayout,
		int tenureMonths,
		MoneyResponse totalPayout,
		List<String> problems) {
}
