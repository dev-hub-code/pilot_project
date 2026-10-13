package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.ledger.service.EarningsBalance;

/**
 * A balance finance may withdraw in cash at the office for an investor today.
 *
 * @param available the whole balance, which is what would be withdrawn
 * @param refusal   why it cannot be withdrawn today; null when it can
 */
public record OfficeWithdrawalOption(EarningsBalance balance, MoneyResponse available, String refusal) {
}
