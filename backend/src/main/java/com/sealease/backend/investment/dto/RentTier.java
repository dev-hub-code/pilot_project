package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.MoneyResponse;
import java.math.BigDecimal;

/**
 * One band of the monthly rent by order value.
 *
 * @param fromAmount         the lowest order total the rate applies to
 * @param toAmount           the highest order total it applies to (inclusive); {@code null} for no upper limit
 * @param monthlyRentPercent rent paid every month, as a percentage of each container's price
 */
public record RentTier(MoneyResponse fromAmount, MoneyResponse toAmount, BigDecimal monthlyRentPercent) {
}
