package com.sealease.backend.earning.dto;

import java.time.Instant;

/**
 * The day of the month payouts are made. It applies to containers bought from then on; schedules
 * already made keep their dates.
 */
public record PayoutSettingsResponse(int payoutDay, Instant updatedAt) {
}
