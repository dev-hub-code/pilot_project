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
 * @param amount         the price of all the containers: what the investor pays for the line
 * @param tenureYears    the lease the investor chose for these containers
 * @param minTenureYears the plan's shortest lease, and {@code maxTenureYears} its longest (to choose from)
 * @param totalPayout    the rent over the chosen lease
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
		int tenureYears,
		int minTenureYears,
		int maxTenureYears,
		MoneyResponse totalPayout,
		List<String> problems) {
}
