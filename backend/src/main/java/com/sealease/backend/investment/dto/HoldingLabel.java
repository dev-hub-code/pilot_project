package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.Money;

import java.util.UUID;

/**
 * How to name a holding to people: its plan and what was paid for it.
 *
 * @param paidAtOffice its order was paid in cash at the office, so its rent goes to the investor's cash balance
 */
public record HoldingLabel(UUID holdingId, String productCode, String productTitle,
		int tenureMonths, Money amount, boolean paidAtOffice) {
}
