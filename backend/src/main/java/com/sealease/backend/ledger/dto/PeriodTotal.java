package com.sealease.backend.ledger.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.Direction;
import com.sealease.backend.ledger.service.TransactionType;

/** Everything posted in a period to one kind of account, by one kind of transaction, on one side. */
public record PeriodTotal(TransactionType transactionType, AccountType accountType, Direction direction, Money amount) {
}
