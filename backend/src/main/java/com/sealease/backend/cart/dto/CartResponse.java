package com.sealease.backend.cart.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * @param total     what the investor pays: the price of everything; {@code null} when the cart is empty
 * @param minimumOrder  the smallest total an order may have; zero when there is none
 * @param problems      why the cart as a whole cannot be checked out (e.g. below the minimum order); empty when it can
 * @param checkoutReady every line can be checked out as it is, and so can the cart as a whole
 */
public record CartResponse(List<CartLineResponse> items, MoneyResponse total, MoneyResponse minimumOrder,
		List<String> problems, boolean checkoutReady) {
}
