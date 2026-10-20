package com.sealease.backend.marketplace.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;

import java.math.BigDecimal;

/**
 * An open container listing as the public website shows it: its published terms only. Rent is set by each order's
 * total value, so none is given here.
 *
 * @param price           per container
 * @param minTenureYears  the shortest lease an investor may choose; {@code maxTenureYears} the longest
 */
public record PublicPlan(
		String code,
		String title,
		String summary,
		ContainerType containerType,
		String containerTypeLabel,
		MoneyResponse price,
		int minTenureYears,
		int maxTenureYears) {

	public static PublicPlan from(MarketplaceListing l) {
		return new PublicPlan(l.code(), l.title(), l.summary(), l.containerType(), l.containerType().label(), l.price(),
				l.minTenureYears(), l.maxTenureYears());
	}

}
