package com.sealease.backend.earning.dto;

import jakarta.validation.constraints.NotNull;

/** A new credit interval, in minutes: one of the offered options. */
public record RentCreditSettingsRequest(@NotNull Integer intervalMinutes) {
}
