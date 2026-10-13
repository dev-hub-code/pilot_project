package com.sealease.backend.earning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param payoutDay 1-31; past a month's last day, payouts are made on its last day */
public record PayoutSettingsRequest(@NotNull @Min(1) @Max(31) Integer payoutDay) {
}
