package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.Money;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What the earnings module needs to schedule and pay a holding's monthly payouts.
 *
 * @param amount the price paid, returned in full over the tenure
 */
public record PayoutTerms(UUID holdingId, UUID userId, UUID productId, String productCode, String containerNumber,
		Money amount, Money monthlyRent, int tenureMonths, LocalDate leaseStartsOn) {
}
