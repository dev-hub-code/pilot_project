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
 * @param price               per container, without GST (rent is based on it)
 * @param priceWithGst        what the investor pays per container: the price plus GST at {@code gstRatePercent}
 * @param minTenureYears      the shortest lease an investor may choose; the investor picks whole years
 * @param maxTenureYears      the longest lease an investor may choose
 * @param totalPayout         the rent over the shortest lease
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
		BigDecimal gstRatePercent,
		MoneyResponse gstPerContainer,
		MoneyResponse priceWithGst,
		BigDecimal monthlyRentPercent,
		BigDecimal monthlyCapitalReturnPercent,
		BigDecimal monthlyPayoutPercent,
		int minTenureYears,
		int maxTenureYears,
		MoneyResponse monthlyRent,
		MoneyResponse monthlyCapitalReturn,
		MoneyResponse monthlyPayout,
		MoneyResponse totalPayout,
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
				t.description(), MoneyResponse.from(t.pricePerContainer()), t.gstRatePercent(),
				MoneyResponse.from(t.gstPerContainer()), MoneyResponse.from(t.pricePerContainerWithGst()), t.monthlyRentPercent(),
				t.monthlyCapitalReturnPercent(), t.monthlyPayoutPercent(), t.minTenureYears(), t.maxTenureYears(),
				MoneyResponse.from(t.monthlyRent()), MoneyResponse.from(t.monthlyCapitalReturn()),
				MoneyResponse.from(t.monthlyPayout()), MoneyResponse.from(t.totalPayout(t.minTenureYears())),
				t.riskDisclosure(), t.termsAndConditions(), containersSold, p.getPublishedAt(), p.getClosedAt(), p.getCancelledAt(),
				p.getCancellationReason(), p.getCreatedAt(), p.getVersion());
	}

}
