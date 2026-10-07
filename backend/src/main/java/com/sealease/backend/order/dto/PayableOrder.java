package com.sealease.backend.order.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.order.entity.InvestmentOrder;
import com.sealease.backend.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/** What the payment module needs to know about an order. */
public record PayableOrder(UUID id, String orderNumber, UUID userId, OrderStatus status, Money total,
		Instant expiresAt) {

	public static PayableOrder of(InvestmentOrder o) {
		return new PayableOrder(o.getId(), o.getOrderNumber(), o.getUserId(), o.getStatus(), o.total(),
				o.getExpiresAt());
	}

	public boolean acceptsPaymentAt(Instant now) {
		return status == OrderStatus.PENDING_PAYMENT && now.isBefore(expiresAt);
	}

}
