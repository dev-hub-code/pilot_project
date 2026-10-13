package com.sealease.backend.withdrawal.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A file kept with a payout batch (never its content, which holds full account numbers).
 *
 * @param kind        PAYMENT (the bank file the batch is paid with) or RECONCILIATION (uploaded back after paying)
 * @param uploadedBy  who uploaded a reconciliation file; null for the generated payment file
 * @param paidCount   withdrawals a reconciliation file marked paid; null for the payment file
 * @param failedCount withdrawals a reconciliation file marked failed; null for the payment file
 */
public record BatchFileResponse(UUID id, String kind, String filename, int sizeBytes, UUID uploadedBy, Integer paidCount,
		Integer failedCount, Instant createdAt) {
}
