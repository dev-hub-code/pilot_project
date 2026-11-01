package com.sealease.backend.order.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Buys units of one plan.
 *
 * @param units        how many units; what they cost follows from the plan's unit price
 * @param acceptTerms  the investor accepts the plan's terms
 */
public record CheckoutRequest(
		@NotNull UUID productId,
		@NotNull @Min(1) @Max(100000000) Integer units,
		@NotNull @AssertTrue(message = "Accept the plan's terms to invest") Boolean acceptTerms) {
}
