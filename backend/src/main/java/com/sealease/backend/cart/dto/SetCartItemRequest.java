package com.sealease.backend.cart.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** The amount is in the offering's currency. */
public record SetCartItemRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount) {
}
