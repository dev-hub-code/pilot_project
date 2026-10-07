package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;

/**
 * Payouts falling due in one calendar month, split into what has been paid and what is still
 * scheduled.
 *
 * @param month {@code yyyy-MM}, by due date
 */
public record PayoutMonth(String month, MoneyResponse rentPaid, MoneyResponse capitalPaid, MoneyResponse rentScheduled,
		MoneyResponse capitalScheduled) {
}
