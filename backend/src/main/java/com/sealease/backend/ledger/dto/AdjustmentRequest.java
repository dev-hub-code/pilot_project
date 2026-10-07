package com.sealease.backend.ledger.dto;

import com.sealease.backend.common.validation.IsoCurrency;
import com.sealease.backend.ledger.service.Direction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A manual correction to an investor's earnings balance, booked against platform adjustments.
 *
 * @param direction CREDIT adds to what the investor is owed, DEBIT recovers an over-payment
 */
public record AdjustmentRequest(
		@NotNull UUID userId,
		@NotBlank @IsoCurrency String currency,
		@NotNull Direction direction,
		@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
		@NotBlank @Size(max = 250) String reason) {
}
