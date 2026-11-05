package com.sealease.backend.payment.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.time.Instant;
import java.util.UUID;

/** Money the gateway took for a reference that matches none of our payments, kept for finance to trace and refund. */
public record UnmatchedReceiptResponse(UUID id, String provider, String providerReference, String gatewayPaymentId,
		MoneyResponse amount, Instant receivedAt, UUID resolvedBy, Instant resolvedAt, String resolution) {
}
