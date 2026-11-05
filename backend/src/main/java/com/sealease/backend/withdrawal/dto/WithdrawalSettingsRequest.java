package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Amounts in the platform's default currency, to the paisa. Every field but the minimum may be left out to keep its
 * current value.
 *
 * @param processingDays business days to show investors; 0 stops showing a timeline
 */
public record WithdrawalSettingsRequest(
		@NotNull @DecimalMin("1") @DecimalMax("10000000") @Digits(integer = 8, fraction = 2) BigDecimal minimumAmount,
		@DecimalMin("0") @DecimalMax("100000") @Digits(integer = 6, fraction = 2) BigDecimal feeFlat,
		@DecimalMin("0") @DecimalMax("50") @Digits(integer = 2, fraction = 4) BigDecimal feePercent,
		@DecimalMin("0") @DecimalMax("50") @Digits(integer = 2, fraction = 4) BigDecimal taxPercent,
		@DecimalMin("1") @DecimalMax("1000000000") @Digits(integer = 10, fraction = 2) BigDecimal dualApprovalThreshold,
		@Min(0) @Max(60) Integer processingDays) {

	/** Only the minimum (the earlier form). */
	public static WithdrawalSettingsRequest minimum(BigDecimal minimumAmount) {
		return new WithdrawalSettingsRequest(minimumAmount, null, null, null, null, null);
	}

}
