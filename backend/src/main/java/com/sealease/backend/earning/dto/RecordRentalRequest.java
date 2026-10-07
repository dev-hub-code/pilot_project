package com.sealease.backend.earning.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Rent received from the lessee for one period, in the offering's currency.
 *
 * @param externalReference the bank's reference for the incoming payment, for reconciliation
 * @param note              required when the amount differs from the expected rent (late, partial…)
 */
public record RecordRentalRequest(
		@NotNull UUID productId,
		@NotNull @Min(1) Integer periodNumber,
		@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
		@NotNull LocalDate receivedOn,
		@NotBlank @Size(max = 100) String externalReference,
		@Size(max = 500) String note) {
}
