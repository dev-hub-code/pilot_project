package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * @param totalsByCurrency one total per currency held: amounts in different currencies are never added
 */
public record PortfolioResponse(List<MoneyResponse> totalsByCurrency, int activeHoldings,
		List<HoldingResponse> holdings) {
}
