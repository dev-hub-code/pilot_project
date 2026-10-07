package com.sealease.backend.ledger.service;

import com.sealease.backend.common.money.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * One side of a journal entry.
 *
 * @param ownerUserId the investor for {@link AccountType#INVESTOR_EARNINGS}; {@code null} for platform accounts
 */
public record Posting(AccountType accountType, UUID ownerUserId, Direction direction, Money amount) {

	public Posting {
		Objects.requireNonNull(accountType, "accountType");
		Objects.requireNonNull(direction, "direction");
		Objects.requireNonNull(amount, "amount");
		if (accountType.isPerInvestor() != (ownerUserId != null)) {
			throw new IllegalArgumentException(accountType + " postings " + (accountType.isPerInvestor()
					? "need" : "cannot have") + " an owner");
		}
		if (amount.isNegative()) {
			throw new IllegalArgumentException("Posting amounts are never negative: " + amount);
		}
	}

	public static Posting debit(AccountType type, UUID owner, Money amount) {
		return new Posting(type, owner, Direction.DEBIT, amount);
	}

	public static Posting credit(AccountType type, UUID owner, Money amount) {
		return new Posting(type, owner, Direction.CREDIT, amount);
	}

}
