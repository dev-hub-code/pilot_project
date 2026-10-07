package com.sealease.backend.order.dto;

import com.sealease.backend.order.entity.OrderStatus;

import java.util.UUID;

/** @param q order number, or part of it */
public record OrderSearchCriteria(String q, OrderStatus status, UUID userId) {
}
