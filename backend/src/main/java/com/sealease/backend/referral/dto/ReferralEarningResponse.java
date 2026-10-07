package com.sealease.backend.referral.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One commission. For investors {@code sourceName} is a display name and the user ids are omitted;
 * staff views carry the ids.
 */
public record ReferralEarningResponse(
		UUID id,
		int level,
		String sourceName,
		UUID sourceUserId,
		UUID beneficiaryUserId,
		String productCode,
		int installmentNumber,
		MoneyResponse base,
		BigDecimal ratePercent,
		MoneyResponse amount,
		Instant paidAt) {
}
