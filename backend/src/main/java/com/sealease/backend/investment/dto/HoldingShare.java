package com.sealease.backend.investment.dto;

import com.sealease.backend.common.money.Money;

import java.math.BigDecimal;
import java.util.UUID;

/** One investor's stake in an offering on lease: the basis of their rental share. */
public record HoldingShare(UUID holdingId, UUID userId, Money amount, BigDecimal ownershipPercent) {
}
