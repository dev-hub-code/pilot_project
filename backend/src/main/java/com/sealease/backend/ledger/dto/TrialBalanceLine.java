package com.sealease.backend.ledger.dto;

import com.sealease.backend.common.money.MoneyResponse;

/** Total debits and credits over every entry in one currency; {@code balanced} must always hold. */
public record TrialBalanceLine(String currency, MoneyResponse debits, MoneyResponse credits, boolean balanced) {
}
