package com.sealease.backend.investment.dto;

import com.sealease.backend.referral.dto.ReferralScheme;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full plan view, for staff.
 *
 * @param audience            the investor type it is offered to
 * @param unitsSold      investments made under this plan
 */
public record ProductResponse(
		UUID id,
		String code,
		ProductStatus status,
		ReferralScheme audience,
		String title,
		String summary,
		String currency,
		int tenureYears,
		BigDecimal annualReturnPercent,
		BigDecimal unitPrice,
		int minUnits,
		long unitsSold,
		Instant publishedAt,
		Instant closedAt,
		Instant cancelledAt,
		String cancellationReason,
		Instant createdAt,
		long version) {

	public static ProductResponse from(InvestmentProduct p, long unitsSold) {
		ProductTerms t = p.terms();
		return new ProductResponse(p.getId(), p.getCode(), p.getStatus(), p.getAudience(), t.title(), t.summary(),
				t.currency().getCurrencyCode(), t.tenureYears(), t.annualReturnPercent(), t.unitPrice(), t.minUnits(),
				unitsSold, p.getPublishedAt(), p.getClosedAt(), p.getCancelledAt(),
				p.getCancellationReason(), p.getCreatedAt(), p.getVersion());
	}

}
