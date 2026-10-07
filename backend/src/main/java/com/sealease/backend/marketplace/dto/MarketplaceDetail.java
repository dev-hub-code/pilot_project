package com.sealease.backend.marketplace.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.service.InvestorEligibility;

import java.util.List;
import java.util.UUID;

/**
 * Everything an investor needs to decide, including whether they may invest and why not.
 *
 * @param photoIds photos of containers of the plan's type (the container allocated may differ)
 */
public record MarketplaceDetail(
		MarketplaceListing listing,
		String description,
		MoneyResponse monthlyRent,
		MoneyResponse monthlyCapitalReturn,
		String riskDisclosure,
		String termsAndConditions,
		List<UUID> photoIds,
		InvestorEligibility eligibility) {

	public static MarketplaceDetail from(ProductResponse p, MarketplaceListing listing, List<UUID> photoIds,
			InvestorEligibility eligibility) {
		return new MarketplaceDetail(listing, p.description(), p.monthlyRent(), p.monthlyCapitalReturn(),
				p.riskDisclosure(), p.termsAndConditions(), photoIds, eligibility);
	}

}
