package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.withdrawal.entity.WithdrawalStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One step of a withdrawal's timeline.
 *
 * @param from      null for the first step
 * @param actorId   who made the change (staff views only; null for the investor, the system or a provider)
 * @param actorName (staff views only)
 * @param reason    why, e.g. a rejection or hold reason, or a payout reference
 */
public record WithdrawalEventResponse(WithdrawalStatus from, WithdrawalStatus to, UUID actorId, String actorName,
		String reason, boolean visibleToInvestor, Instant at) {
}
