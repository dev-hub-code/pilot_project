package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** @param minimumAmount in the platform's default currency, to the paisa */
public record WithdrawalSettingsRequest(
		@NotNull @DecimalMin("1") @DecimalMax("10000000") @Digits(integer = 8, fraction = 2) BigDecimal minimumAmount) {
}
