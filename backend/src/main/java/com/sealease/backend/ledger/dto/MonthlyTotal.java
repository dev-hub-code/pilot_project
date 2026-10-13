package com.sealease.backend.ledger.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.ledger.service.AccountType;

import java.time.YearMonth;

/**
 * What one kind of account moved in one calendar month (UTC), net and on its normal side: an expense
 * account's debits less any reversing credits.
 */
public record MonthlyTotal(YearMonth month, AccountType accountType, Money amount) {
}
