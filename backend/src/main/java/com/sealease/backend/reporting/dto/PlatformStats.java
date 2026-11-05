package com.sealease.backend.reporting.dto;

import com.sealease.backend.common.money.MoneyResponse;

/**
 * Public track record of the platform, for the website. Every figure is read from the books, never entered by hand.
 *
 * @param paidToInvestors rent and capital credited to investors so far
 * @param unitsEarning    units whose rent has started being credited
 * @param investors       investors who have bought at least one unit
 * @param units           units bought by investors, on lease or matured
 */
public record PlatformStats(MoneyResponse paidToInvestors, long unitsEarning, long investors, long units) {
}
