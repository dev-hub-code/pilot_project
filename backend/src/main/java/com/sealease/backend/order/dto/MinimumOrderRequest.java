package com.sealease.backend.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** A new minimum order value in the platform's currency; zero for none. */
public record MinimumOrderRequest(@NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal amount) {
}
