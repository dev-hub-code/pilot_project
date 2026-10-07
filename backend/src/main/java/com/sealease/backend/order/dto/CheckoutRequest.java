package com.sealease.backend.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Checkout of the whole cart. The investor confirms, by plan id, that they accept the terms of every
 * plan in the cart.
 */
public record CheckoutRequest(@NotEmpty @Size(max = 20) List<@NotNull UUID> acceptedTerms) {
}
