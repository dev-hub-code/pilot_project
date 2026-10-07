package com.sealease.backend.investment.dto;

import java.util.UUID;

/** How to name a holding to people: its plan and its container. */
public record HoldingLabel(UUID holdingId, String productCode, String productTitle, String containerNumber,
		int tenureMonths) {
}
