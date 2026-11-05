package com.sealease.backend.withdrawal.dto;

import java.util.List;
import java.util.UUID;

/**
 * @param provider the payout provider used
 * @param batches  the payout batches made, one per kind and payout method
 */
public record InitiatePaymentResponse(String provider, List<Batch> batches) {

	public record Batch(UUID id, String reference, int items) {
	}

}
