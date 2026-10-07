package com.sealease.backend.cart.service;

import java.util.UUID;

/** A cart line handed to checkout: how many containers of a plan. */
public record CartLine(UUID productId, int quantity) {
}
