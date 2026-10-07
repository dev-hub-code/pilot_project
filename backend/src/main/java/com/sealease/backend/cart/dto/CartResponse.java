package com.sealease.backend.cart.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * @param total     {@code null} when the cart is empty
 * @param checkoutReady every line can be checked out as it is
 */
public record CartResponse(List<CartLineResponse> items, MoneyResponse total, boolean checkoutReady) {
}
