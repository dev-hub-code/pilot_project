package com.sealease.backend.order.dto;

import com.sealease.backend.common.money.MoneyResponse;
import java.time.Instant;

/**
 * The smallest total an order may have.
 *
 * @param amount zero when there is no minimum
 */
public record MinimumOrderResponse(MoneyResponse amount, Instant updatedAt, String updatedByName) {
}
