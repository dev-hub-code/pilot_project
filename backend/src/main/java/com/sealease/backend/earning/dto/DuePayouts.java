package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;

/** Scheduled payouts that have fallen due and are not paid yet, in one currency. */
public record DuePayouts(long count, MoneyResponse total) {
}
