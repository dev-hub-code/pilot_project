package com.sealease.backend.order.event;

import com.sealease.backend.common.money.Money;

import java.util.UUID;

/**
 * Published in-process when a paid order is confirmed, inside the confirmation transaction.
 * Non-financial listeners (CRM) react after commit so they can never affect a payment.
 */
public record OrderConfirmedEvent(UUID orderId, String orderNumber, UUID userId, Money total) {
}
