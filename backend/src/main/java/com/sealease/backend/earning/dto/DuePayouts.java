package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;

/** Leases with ended hours not credited yet (crediting is behind), in one currency, and what those hours come to. */
public record DuePayouts(long count, MoneyResponse total) {
}
