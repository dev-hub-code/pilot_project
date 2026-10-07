package com.sealease.backend.order.event;

import com.sealease.backend.order.entity.OrderStatus;

import java.util.UUID;

/**
 * Published (in-process, inside the closing transaction) when an order expires or is cancelled,
 * so the payment module can cancel payment attempts still in flight.
 */
public record OrderClosedEvent(UUID orderId, OrderStatus status) {
}
