package com.sealease.backend.marketplace.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A marketplace card for a plan. Payout figures are per container.
 *
 * @param coverPhotoId a photo of a container of the plan's type; {@code null} when there is none
 */
public record MarketplaceListing(
		UUID id,
		String code,
		String title,
		String summary,
		ProductStatus status,
		ContainerType containerType,
		MoneyResponse price,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		BigDecimal monthlyPayoutPercent,
		int tenureMonths,
		MoneyResponse monthlyPayout,
		MoneyResponse totalPayout,
		long availableContainers,
		UUID coverPhotoId) {

	public static MarketplaceListing from(ProductResponse p, UUID coverPhotoId) {
		return new MarketplaceListing(p.id(), p.code(), p.title(), p.summary(), p.status(), p.containerType(), p.price(),
				p.monthlyRentPercent(), p.monthlyCapitalReturnPercent(), p.monthlyPayoutPercent(), p.tenureMonths(),
				p.monthlyPayout(), p.totalPayout(), p.availableContainers(), coverPhotoId);
	}

}
