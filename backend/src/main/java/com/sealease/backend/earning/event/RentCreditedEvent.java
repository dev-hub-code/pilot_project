package com.sealease.backend.earning.event;

import com.sealease.backend.common.money.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published in-process, inside the crediting transaction, each time some hours of a lease's rent are credited to the
 * investor - so listeners (referral commissions) commit or roll back with it.
 *
 * @param creditId     the rent_credits row: identifies these hours
 * @param invested     what the investor paid for the container
 * @param monthsBefore lease months elapsed before these hours (see {@code RentAccrual#monthsAt})
 * @param monthsAfter  lease months elapsed after them: commission is owed on the difference
 */
public record RentCreditedEvent(UUID creditId, UUID holdingId, UUID userId, UUID productId, String productCode,
		Money invested, BigDecimal monthsBefore, BigDecimal monthsAfter, Instant from, Instant to) {
}
