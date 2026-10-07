package com.sealease.backend.ledger.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.ledger.service.TransactionType;

import java.time.Instant;
import java.util.List;

/**
 * An investor's earnings account in one currency over a period: balance before it, every entry in
 * it (signed: credits add to what the investor is owed), and balance after it.
 */
public record InvestorStatement(String currency, Money opening, Money closing, List<Entry> entries) {

	public record Entry(Instant at, TransactionType type, String description, Money amount) {
	}

}
