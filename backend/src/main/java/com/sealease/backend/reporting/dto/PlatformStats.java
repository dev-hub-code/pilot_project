package com.sealease.backend.reporting.dto;

import com.sealease.backend.common.money.MoneyResponse;

/**
 * Public track record of the platform, for the website. Every figure is read from the books, never entered by hand.
 *
 * @param paidToInvestors rent and capital credited to investors so far
 * @param payoutsPaid     monthly payouts credited so far
 * @param investors       investors who have bought at least one container
 * @param containers      containers bought by investors, on lease or matured
 */
public record PlatformStats(MoneyResponse paidToInvestors, long payoutsPaid, long investors, long containers) {
}
