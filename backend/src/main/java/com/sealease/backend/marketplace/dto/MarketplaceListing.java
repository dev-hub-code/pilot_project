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
 * @param price          per container, without GST; {@code priceWithGst} is what the investor pays
 * @param minTenureYears the shortest lease an investor may choose, in years
 * @param maxTenureYears the longest lease an investor may choose, in years
 * @param totalPayout    the rent over the shortest lease
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
		BigDecimal gstRatePercent,
		MoneyResponse priceWithGst,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		BigDecimal monthlyPayoutPercent,
		int minTenureYears,
		int maxTenureYears,
		MoneyResponse monthlyPayout,
		MoneyResponse totalPayout,
		UUID coverPhotoId) {

	public static MarketplaceListing from(ProductResponse p, UUID coverPhotoId) {
		return new MarketplaceListing(p.id(), p.code(), p.title(), p.summary(), p.status(), p.containerType(), p.price(),
				p.gstRatePercent(), p.priceWithGst(),
				p.monthlyRentPercent(), p.monthlyCapitalReturnPercent(), p.monthlyPayoutPercent(), p.minTenureYears(), p.maxTenureYears(),
				p.monthlyPayout(), p.totalPayout(), coverPhotoId);
	}

}
