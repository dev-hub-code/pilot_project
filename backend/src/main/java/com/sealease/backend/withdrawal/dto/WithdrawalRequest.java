package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** @param bankAccountId one of the investor's verified accounts; the withdrawal is in its currency */
public record WithdrawalRequest(
		@NotNull UUID bankAccountId,
		@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {
}
