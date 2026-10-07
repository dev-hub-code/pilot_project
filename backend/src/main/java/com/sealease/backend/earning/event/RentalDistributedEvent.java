package com.sealease.backend.earning.event;

import com.sealease.backend.common.money.Money;

import java.util.List;
import java.util.UUID;

/**
 * Published in-process, inside the distribution transaction, once a rental receipt has been
 * credited to investors - so listeners (referral commissions) commit or roll back with it.
 */
public record RentalDistributedEvent(UUID receiptId, UUID productId, String productCode, int periodNumber,
		UUID approvedBy, List<Share> shares) {

	public RentalDistributedEvent {
		shares = List.copyOf(shares);
	}

	/** One investor's credited share; {@code gross} is before the management fee. */
	public record Share(UUID earningId, UUID userId, Money gross) {
	}

}
