package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * What the earnings module needs to schedule and pay a holding's monthly payouts.
 *
 * @param amount          the price paid
 * @param capitalReturned paid back over the tenure: the whole price under terms sold before capital returns
 *                        ended, otherwise zero (rent only)
 * @param leaseStartsAt   the lease's first hour: rent is credited hourly from then
 */
public record PayoutTerms(UUID holdingId, UUID userId, UUID productId, String productCode, String containerNumber,
		Money amount, Money monthlyRent, Money capitalReturned, int tenureMonths, Instant leaseStartsAt) {
}
