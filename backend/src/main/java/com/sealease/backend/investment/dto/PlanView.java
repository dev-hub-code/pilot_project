package com.sealease.backend.investment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;

import java.math.BigDecimal;
import java.util.UUID;

/** A plan's published terms, as investors and the website see them; the website gets no id, only the public code. */
public record PlanView(
		@JsonInclude(JsonInclude.Include.NON_NULL) UUID id,
		String code,
		String title,
		String summary,
		ProductStatus status,
		int tenureYears,
		BigDecimal annualReturnPercent,
		MoneyResponse unitPrice,
		int minUnits) {

	public static PlanView of(InvestmentProduct p) {
		ProductTerms t = p.terms();
		return new PlanView(p.getId(), p.getCode(), t.title(), t.summary(), p.getStatus(), t.tenureYears(),
				t.annualReturnPercent(), MoneyResponse.from(Money.of(t.unitPrice(), t.currency())), t.minUnits());
	}

	/** The same terms for anyone who is not signed in. */
	public PlanView withoutId() {
		return new PlanView(null, code, title, summary, status, tenureYears, annualReturnPercent, unitPrice, minUnits);
	}

}
