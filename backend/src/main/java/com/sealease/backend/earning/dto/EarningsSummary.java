package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param balances       what the platform owes the investor now (their wallet), per currency
 * @param rentPaid       rent credited over all time, per currency
 * @param capitalReturned capital credited back over all time, per currency
 * @param referralEarned commission on referrals' rent credited over all time, per currency
 * @param nextPayout     the next scheduled payout across all containers; {@code null} when none
 */
public record EarningsSummary(List<MoneyResponse> balances, List<MoneyResponse> rentPaid,
		List<MoneyResponse> capitalReturned, List<MoneyResponse> referralEarned, NextPayout nextPayout, List<HoldingPayouts> holdings) {

	public record NextPayout(LocalDate dueOn, MoneyResponse total) {
	}

	/** Progress of one container's payouts. */
	public record HoldingPayouts(UUID holdingId, UUID productId, String containerNumber, int paid, int installments,
			MoneyResponse received, LocalDate nextDueOn, MoneyResponse nextAmount, Instant lastPaidAt) {
	}

}
