package com.sealease.backend.ledger.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.ledger.service.Direction;
import com.sealease.backend.ledger.service.TransactionType;

import java.time.Instant;
import java.util.UUID;

/** One line on an account statement, with the transaction it belongs to. */
public record LedgerEntryResponse(UUID id, UUID transactionId, TransactionType transactionType, String reference,
		String description, Direction direction, MoneyResponse amount, Instant createdAt) {
}
