package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;

/**
 * Rent for one calendar month (UTC): what has been credited to the wallet and what is still to come this month.
 *
 * @param month {@code yyyy-MM}
 */
public record PayoutMonth(String month, MoneyResponse rentPaid, MoneyResponse capitalPaid, MoneyResponse rentScheduled,
		MoneyResponse capitalScheduled) {
}
