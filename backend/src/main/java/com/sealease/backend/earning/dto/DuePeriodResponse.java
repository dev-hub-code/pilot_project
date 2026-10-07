package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A rental period of an offering on lease whose rent has fallen due and has not been recorded.
 *
 * @param dueOn first day after the period (rent is paid in arrears)
 */
public record DuePeriodResponse(UUID productId, String productCode, String productTitle, int periodNumber,
		int periodCount, LocalDate periodStartsOn, LocalDate dueOn, MoneyResponse expectedAmount, long daysOverdue) {
}
