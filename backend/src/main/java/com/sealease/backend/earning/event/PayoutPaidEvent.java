package com.sealease.backend.earning.event;

import com.sealease.backend.common.money.Money;

import java.util.UUID;

/**
 * Published in-process, inside the payout transaction, once an installment has been credited to the
 * investor - so listeners (referral commissions) commit or roll back with it.
 *
 * @param invested what the investor paid for the container this payout is for
 */
public record PayoutPaidEvent(UUID installmentId, UUID userId, UUID productId, String productCode,
		int installmentNumber, Money invested) {
}
