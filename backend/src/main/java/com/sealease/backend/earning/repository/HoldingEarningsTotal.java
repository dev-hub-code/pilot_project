package com.sealease.backend.earning.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Net earnings of one holding so far. */
public record HoldingEarningsTotal(UUID holdingId, UUID productId, String currency, BigDecimal net, long payments,
		Instant lastPaidAt) {
}
