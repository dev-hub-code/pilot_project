package com.sealease.backend.marketplace.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.dto.ContainerDocumentResponse;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.service.InvestorEligibility;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything an investor needs to decide, including whether they may invest and why not.
 *
 * @param managementFeePercent deducted from every rental payment; listed yields are already net of it
 */
public record MarketplaceDetail(
		MarketplaceListing listing,
		String description,
		MoneyResponse investmentIncrement,
		MoneyResponse maximumPerInvestor,
		BigDecimal managementFeePercent,
		String lesseeName,
		String riskDisclosure,
		String termsAndConditions,
		String termsVersion,
		Instant offerOpensAt,
		List<ContainerDocumentResponse> documents,
		InvestorEligibility eligibility) {

	public static MarketplaceDetail from(ProductResponse p, MarketplaceListing listing,
			List<ContainerDocumentResponse> documents, InvestorEligibility eligibility) {
		return new MarketplaceDetail(listing, p.description(), p.investmentIncrement(), p.maximumPerInvestor(),
				p.managementFeePercent(), p.lesseeName(), p.riskDisclosure(), p.termsAndConditions(), p.termsVersion(),
				p.offerOpensAt(), documents, eligibility);
	}

}
