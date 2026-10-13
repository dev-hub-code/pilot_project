package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One month's rent or referral withdrawal run.
 *
 * @param kind        RENT or REFERRAL
 * @param triggeredBy who ran it by hand; null when the schedule ran it
 * @param batchId     the batch holding the withdrawals it made; null when it made none
 * @param skipped     investors with a balance to withdraw who were left out, and why
 */
public record ScheduledRunResponse(UUID id, WithdrawalKind kind, String period, String currency, Instant runAt, UUID triggeredBy, UUID batchId,
		String batchReference, int withdrawalCount, MoneyResponse total, List<Skipped> skipped) {

	public record Skipped(UUID userId, String reason) {
	}

}
