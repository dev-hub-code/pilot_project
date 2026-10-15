package com.sealease.backend.order.dto;

import com.sealease.backend.order.entity.OrderStatus;

import java.util.UUID;

/**
 * @param q        order number, or part of it (a whole one when {@code exact})
 * @param exact    {@code q} must be a whole order number, or name an investor ({@code qInvestorId}); nothing without it
 * @param qInvestorId the investor {@code q} names exactly, if any: their orders match too
 */
public record OrderSearchCriteria(String q, OrderStatus status, UUID userId, boolean exact, UUID qInvestorId) {

	public OrderSearchCriteria(String q, OrderStatus status, UUID userId) {
		this(q, status, userId, false, null);
	}

}
