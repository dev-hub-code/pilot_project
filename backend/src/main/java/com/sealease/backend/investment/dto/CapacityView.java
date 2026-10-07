package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.entity.InvestmentProduct;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Funding state of an offering. {@code fundedPercent} counts confirmed (committed) capital only. */
public record CapacityView(MoneyResponse total, MoneyResponse committed, MoneyResponse reserved,
		MoneyResponse available, BigDecimal fundedPercent) {

	public static CapacityView of(InvestmentProduct p) {
		BigDecimal funded = p.committed().amount().multiply(BigDecimal.valueOf(100))
			.divide(p.total().amount(), 2, RoundingMode.HALF_UP);
		return new CapacityView(MoneyResponse.from(p.total()), MoneyResponse.from(p.committed()),
				MoneyResponse.from(p.reserved()), MoneyResponse.from(p.available()), funded);
	}

}
