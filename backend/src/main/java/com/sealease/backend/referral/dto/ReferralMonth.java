package com.sealease.backend.referral.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * Commission earned in one calendar month, per level.
 *
 * @param month  {@code yyyy-MM}, UTC
 * @param levels commission at levels 1-4, in order (zero where none was earned)
 */
public record ReferralMonth(String month, List<MoneyResponse> levels, MoneyResponse total) {
}
