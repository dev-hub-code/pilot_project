package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param balances    what the platform owes the investor now, per currency (earnings ± adjustments)
 * @param totalEarned net rental credited over all time, per currency
 */
public record EarningsSummary(List<MoneyResponse> balances, List<MoneyResponse> totalEarned,
		List<HoldingEarnings> holdings) {

	public record HoldingEarnings(UUID holdingId, UUID productId, MoneyResponse earned, long payments,
			Instant lastPaidAt) {
	}

}
