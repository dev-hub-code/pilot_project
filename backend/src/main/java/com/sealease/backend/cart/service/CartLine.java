package com.sealease.backend.cart.service;

import com.sealease.backend.common.money.Money;

import java.util.UUID;

/** A cart line handed to checkout. */
public record CartLine(UUID productId, Money amount) {
}
