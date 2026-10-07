package com.sealease.backend.marketplace.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.investment.dto.CapacityView;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.RentalFrequency;
import com.sealease.backend.investment.entity.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A marketplace card. */
public record MarketplaceListing(
		UUID id,
		String code,
		String title,
		String summary,
		InvestmentType investmentType,
		ProductStatus status,
		MoneyResponse price,
		MoneyResponse minimumInvestment,
		MoneyResponse expectedRentalAmount,
		RentalFrequency rentalFrequency,
		BigDecimal expectedAnnualReturnPercent,
		int durationMonths,
		RiskLevel riskLevel,
		CapacityView capacity,
		ContainerSummary container,
		UUID coverPhotoId,
		Instant offerClosesAt) {

	public static MarketplaceListing from(ProductResponse p, UUID coverPhotoId) {
		return new MarketplaceListing(p.id(), p.code(), p.title(), p.summary(), p.investmentType(), p.status(), p.price(),
				p.minimumInvestment(), p.expectedRentalAmount(), p.rentalFrequency(), p.expectedAnnualReturnPercent(),
				p.durationMonths(), p.riskLevel(), p.capacity(), p.container(), coverPhotoId, p.offerClosesAt());
	}

}
