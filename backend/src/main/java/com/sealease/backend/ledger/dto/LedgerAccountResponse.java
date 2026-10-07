package com.sealease.backend.ledger.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.Direction;

import java.time.Instant;
import java.util.UUID;

/** @param balance on the account's normal side (see {@link AccountType#normalBalance()}) */
public record LedgerAccountResponse(UUID id, AccountType accountType, UUID ownerUserId, Direction normalBalance,
		MoneyResponse balance, Instant createdAt) {
}
