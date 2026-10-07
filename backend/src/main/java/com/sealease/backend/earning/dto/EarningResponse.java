package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One rental payment credited to the investor: their gross share, the fee deducted and the net. */
public record EarningResponse(
		UUID id,
		UUID holdingId,
		UUID productId,
		String productCode,
		String productTitle,
		int periodNumber,
		LocalDate periodStartsOn,
		LocalDate periodEndsOn,
		BigDecimal ownershipPercent,
		MoneyResponse gross,
		MoneyResponse fee,
		MoneyResponse net,
		Instant paidAt) {
}
