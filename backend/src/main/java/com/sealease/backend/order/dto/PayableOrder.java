package com.sealease.backend.order.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.order.entity.InvestmentOrder;
import com.sealease.backend.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * What the payment module needs to know about an order.
 *
 * @param total           what the investor pays: the price plus GST
 * @param totalWithoutGst the price alone, what an investor paying cash at the office pays
 */
public record PayableOrder(UUID id, String orderNumber, UUID userId, OrderStatus status, Money total,
		Money totalWithoutGst, Instant expiresAt) {

	public static PayableOrder of(InvestmentOrder o, Money totalWithoutGst) {
		return new PayableOrder(o.getId(), o.getOrderNumber(), o.getUserId(), o.getStatus(), o.total(),
				totalWithoutGst, o.getExpiresAt());
	}

	public Money due(boolean gstIncluded) {
		return gstIncluded ? total : totalWithoutGst;
	}

	public boolean acceptsPaymentAt(Instant now) {
		return status == OrderStatus.PENDING_PAYMENT && now.isBefore(expiresAt);
	}

}
