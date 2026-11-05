package com.sealease.backend.withdrawal.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Withdrawal settings, in the platform's default currency.
 *
 * @param minimumAmount           the smallest withdrawal; a run pays an investor's row only when it comes to at least this
 * @param defaultAmount           the configured default minimum, which applies while no admin has set one
 * @param feeFlat                 a fixed charge per withdrawal (0: none)
 * @param feePercent              a charge as a percentage of the amount withdrawn (0: none)
 * @param taxPercent              tax withheld, as a percentage of the amount withdrawn (0: none)
 * @param dualApprovalThreshold   requests above this need a second, different approver
 * @param defaultDualApprovalThreshold the configured default threshold, which applies while no admin has set one
 * @param processingDays          business days investors are told a reviewed request takes to be paid; null: not shown
 * @param updatedAt               when an admin last changed them; null while the defaults apply
 */
public record WithdrawalSettingsResponse(BigDecimal minimumAmount, String currency, BigDecimal defaultAmount,
		BigDecimal feeFlat, BigDecimal feePercent, BigDecimal taxPercent, BigDecimal dualApprovalThreshold,
		BigDecimal defaultDualApprovalThreshold, Integer processingDays, Instant updatedAt) {
}
