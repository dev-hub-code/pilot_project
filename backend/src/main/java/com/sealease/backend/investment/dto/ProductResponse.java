package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full plan view (staff, and the basis of the investor detail view). Payout figures are per
 * container.
 *
 * @param price               per container (rent is based on it)
 * @param minTenureYears      the shortest lease an investor may choose; the investor picks whole years
 * @param maxTenureYears      the longest lease an investor may choose
 * @param containersSold      containers bought under this plan
 */
public record ProductResponse(
		UUID id,
		String code,
		ProductStatus status,
		ContainerType containerType,
		String title,
		String summary,
		String description,
		MoneyResponse price,
		int minTenureYears,
		int maxTenureYears,
		String riskDisclosure,
		String termsAndConditions,
		long containersSold,
		Instant publishedAt,
		Instant closedAt,
		Instant cancelledAt,
		String cancellationReason,
		Instant createdAt,
		long version) {

	public static ProductResponse from(InvestmentProduct p, long containersSold) {
		ProductTerms t = p.terms();
		return new ProductResponse(p.getId(), p.getCode(), p.getStatus(), t.containerType(), t.title(), t.summary(),
				t.description(), MoneyResponse.from(t.pricePerContainer()), t.minTenureYears(),
				t.maxTenureYears(),
				t.riskDisclosure(), t.termsAndConditions(), containersSold, p.getPublishedAt(), p.getClosedAt(), p.getCancelledAt(),
				p.getCancellationReason(), p.getCreatedAt(), p.getVersion());
	}

}
