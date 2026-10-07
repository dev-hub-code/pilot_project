package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A receipt with how it was (or, while RECORDED, would be) split. {@code toInvestors + fees +
 * retained} equals the amount received.
 *
 * @param retained the unsold share of the offering plus rounding remainders, kept by the platform
 */
public record RentalReceiptDetail(
		RentalReceiptResponse receipt,
		boolean preview,
		List<Line> distribution,
		MoneyResponse toInvestors,
		MoneyResponse fees,
		MoneyResponse retained) {

	public record Line(UUID holdingId, UUID userId, BigDecimal ownershipPercent, MoneyResponse gross,
			MoneyResponse fee, MoneyResponse net) {
	}

}
