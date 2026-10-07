package com.sealease.backend.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param externalReference the bank's reference for the incoming transfer, for reconciliation
 * @param amountReceived    what arrived, in the payment's currency; must equal the amount due
 */
public record ConfirmTransferRequest(
		@NotBlank @Size(max = 100) String externalReference,
		@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amountReceived) {
}
