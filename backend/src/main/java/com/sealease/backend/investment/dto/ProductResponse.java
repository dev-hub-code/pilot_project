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
 * @param availableContainers containers of the plan's type in inventory now (shared by plans of that type)
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
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		BigDecimal monthlyPayoutPercent,
		int tenureMonths,
		MoneyResponse monthlyRent,
		MoneyResponse monthlyCapitalReturn,
		MoneyResponse monthlyPayout,
		MoneyResponse totalPayout,
		String riskDisclosure,
		String termsAndConditions,
		long availableContainers,
		long containersSold,
		Instant publishedAt,
		Instant closedAt,
		Instant cancelledAt,
		String cancellationReason,
		Instant createdAt,
		long version) {

	public static ProductResponse from(InvestmentProduct p, long availableContainers, long containersSold) {
		ProductTerms t = p.terms();
		return new ProductResponse(p.getId(), p.getCode(), p.getStatus(), t.containerType(), t.title(), t.summary(),
				t.description(), MoneyResponse.from(t.pricePerContainer()), t.monthlyRentPercent(),
				t.monthlyCapitalReturnPercent(), t.monthlyPayoutPercent(), t.tenureMonths(),
				MoneyResponse.from(t.monthlyRent()), MoneyResponse.from(t.monthlyCapitalReturn()),
				MoneyResponse.from(t.monthlyPayout()), MoneyResponse.from(t.totalPayout()),
				t.riskDisclosure(), t.termsAndConditions(), availableContainers, containersSold, p.getPublishedAt(), p.getClosedAt(), p.getCancelledAt(),
				p.getCancellationReason(), p.getCreatedAt(), p.getVersion());
	}

}
