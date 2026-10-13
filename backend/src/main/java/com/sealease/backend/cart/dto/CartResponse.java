package com.sealease.backend.cart.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * @param subtotal  the price of everything, without GST; {@code null} when the cart is empty
 * @param gstTotal  GST on the subtotal
 * @param total     what the investor pays: the subtotal plus GST; {@code null} when the cart is empty
 * @param checkoutReady every line can be checked out as it is
 */
public record CartResponse(List<CartLineResponse> items, MoneyResponse subtotal, MoneyResponse gstTotal,
		MoneyResponse total, boolean checkoutReady) {
}
