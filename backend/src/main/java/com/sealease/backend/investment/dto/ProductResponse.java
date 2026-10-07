package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.entity.RentalFrequency;
import com.sealease.backend.investment.entity.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Full offering view (staff, and the basis of the investor detail view). */
public record ProductResponse(
		UUID id,
		String code,
		ProductStatus status,
		InvestmentType investmentType,
		String title,
		String summary,
		String description,
		MoneyResponse price,
		MoneyResponse minimumInvestment,
		MoneyResponse investmentIncrement,
		MoneyResponse maximumPerInvestor,
		MoneyResponse expectedRentalAmount,
		RentalFrequency rentalFrequency,
		BigDecimal expectedAnnualReturnPercent,
		int durationMonths,
		String lesseeName,
		RiskLevel riskLevel,
		String riskDisclosure,
		String termsAndConditions,
		String termsVersion,
		Instant offerOpensAt,
		Instant offerClosesAt,
		CapacityView capacity,
		ContainerSummary container,
		Instant publishedAt,
		Instant cancelledAt,
		String cancellationReason,
		Instant createdAt,
		long version) {

	public static ProductResponse from(InvestmentProduct p, ContainerSummary container) {
		ProductTerms t = p.terms();
		return new ProductResponse(p.getId(), p.getCode(), p.getStatus(), t.investmentType(), t.title(), t.summary(),
				t.description(), money(t.totalAmount(), t), money(t.minimumInvestment(), t),
				money(t.investmentIncrement(), t), money(t.maximumPerInvestor(), t), money(t.expectedRentalAmount(), t),
				t.rentalFrequency(), t.expectedAnnualReturnPercent(), t.durationMonths(), t.lesseeName(), t.riskLevel(),
				t.riskDisclosure(), t.termsAndConditions(), t.termsVersion(), t.offerOpensAt(), t.offerClosesAt(),
				CapacityView.of(p), container, p.getPublishedAt(), p.getCancelledAt(), p.getCancellationReason(),
				p.getCreatedAt(), p.getVersion());
	}

	private static MoneyResponse money(BigDecimal amount, ProductTerms t) {
		return amount == null ? null : MoneyResponse.from(Money.of(amount, t.currency()));
	}

}
