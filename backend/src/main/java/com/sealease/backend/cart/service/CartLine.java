package com.sealease.backend.cart.service;

import java.util.UUID;

/** A cart line handed to checkout: how many containers of a plan, leased for how many years. */
public record CartLine(UUID productId, int quantity, int tenureYears) {
}
