package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * @param totalsByCurrency one total invested per currency: amounts in different currencies are never added
 * @param activeHoldings   containers currently on lease
 */
public record PortfolioResponse(List<MoneyResponse> totalsByCurrency, int activeHoldings,
		List<HoldingResponse> holdings) {
}
