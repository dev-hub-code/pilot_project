package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Days 2-28: the file is made the day before, and the 28th exists in every month. */
public record WithdrawalSettingsRequest(
		@NotNull @Min(2) @Max(28) Integer rentWithdrawalDay,
		@NotNull @Min(2) @Max(28) Integer referralWithdrawalDay) {
}
