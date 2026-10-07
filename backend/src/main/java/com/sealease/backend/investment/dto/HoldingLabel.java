package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.Money;

import java.util.UUID;

/** How to name a holding to people: its plan and its container, and what was paid for it. */
public record HoldingLabel(UUID holdingId, String productCode, String productTitle, String containerNumber,
		int tenureMonths, Money amount) {
}
