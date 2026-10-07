package com.sealease.backend.marketplace.dto;

import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.RiskLevel;

/** Investor-facing filters. {@code status} is restricted to listed statuses; default OPEN. */
public record MarketplaceCriteria(InvestmentType investmentType, ContainerType containerType, RiskLevel riskLevel,
		ProductStatus status, MarketplaceSort sort) {
}
