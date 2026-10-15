package com.sealease.backend.referral.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Commission at one level on one referred container's rent in one month. For investors {@code sourceName} is a
 * display name and the user ids are omitted; staff views carry the ids.
 *
 * @param month  {@code yyyy-MM}, UTC
 * @param paidAt when the latest commission in it was credited
 */
public record ReferralEarningResponse(
		UUID id,
		int level,
		String sourceName,
		UUID sourceUserId,
		UUID beneficiaryUserId,
		String productCode,
		String month,
		MoneyResponse base,
		BigDecimal ratePercent,
		MoneyResponse amount,
		Instant paidAt) {
}
