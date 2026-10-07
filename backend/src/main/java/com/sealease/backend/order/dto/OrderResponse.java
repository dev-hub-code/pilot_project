package com.sealease.backend.order.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.order.entity.InvestmentOrder;
import com.sealease.backend.order.entity.OrderItem;
import com.sealease.backend.order.entity.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @param expiresAt payment is due by then; afterwards the reservation lapses */
public record OrderResponse(
		UUID id,
		String orderNumber,
		UUID userId,
		OrderStatus status,
		MoneyResponse total,
		Instant expiresAt,
		Instant confirmedAt,
		Instant closedAt,
		String closeReason,
		Instant createdAt,
		List<OrderItemResponse> items) {

	public static OrderResponse from(InvestmentOrder o, List<OrderItem> items) {
		return new OrderResponse(o.getId(), o.getOrderNumber(), o.getUserId(), o.getStatus(),
				MoneyResponse.from(o.total()), o.getExpiresAt(), o.getConfirmedAt(), o.getClosedAt(), o.getCloseReason(),
				o.getCreatedAt(), items.stream().map(OrderItemResponse::from).toList());
	}

}
