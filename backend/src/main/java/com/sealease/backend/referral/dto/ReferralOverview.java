package com.sealease.backend.referral.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The investor's referral programme at a glance.
 *
 * @param eligible    whether the investor currently earns commissions (active account, verified identity)
 * @param referredBy  display name of the direct referrer, if any
 */
public record ReferralOverview(
		String code,
		String referredBy,
		boolean eligible,
		String ineligibleReason,
		List<Level> levels,
		List<MoneyResponse> totalEarned,
		Instant ratesEffectiveFrom) {

	/** @param members referred investors at this depth */
	public record Level(int level, BigDecimal ratePercent, long members) {
	}

}
