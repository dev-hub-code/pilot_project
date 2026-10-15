package com.sealease.backend.withdrawal.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The minimum withdrawal: a run pays an investor's row only when it comes to at least {@code minimumAmount}.
 *
 * @param currency      the currency it is in (the platform's default)
 * @param defaultAmount the configured default, which applies while no admin has set one
 * @param updatedAt     when an admin last set it; null while the default applies
 */
public record WithdrawalSettingsResponse(BigDecimal minimumAmount, String currency, BigDecimal defaultAmount,
		Instant updatedAt) {
}
