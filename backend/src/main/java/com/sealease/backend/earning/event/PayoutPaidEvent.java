package com.sealease.backend.earning.event;

import com.sealease.backend.common.money.Money;

import java.util.UUID;

/**
 * Published in-process, inside the payout transaction, once an installment has been credited to the
 * investor - so listeners (referral commissions) commit or roll back with it.
 *
 * @param rent the rent part of the payout (the capital part is the investor's own money)
 */
public record PayoutPaidEvent(UUID installmentId, UUID userId, UUID productId, String productCode,
		int installmentNumber, Money rent) {
}
