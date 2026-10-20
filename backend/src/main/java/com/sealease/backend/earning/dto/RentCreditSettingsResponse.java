package com.sealease.backend.earning.dto;

import java.time.Instant;
import java.util.List;

/**
 * How often rent is credited.
 *
 * @param intervalMinutes the interval in force
 * @param label           e.g. "every hour"
 * @param defaultMinutes  the configured default, which applies until an admin sets one
 * @param options         the intervals an admin may choose
 * @param updatedAt       when an admin last set it; {@code null} while the default applies
 */
public record RentCreditSettingsResponse(long intervalMinutes, String label, long defaultMinutes, List<Option> options,
		Instant updatedAt, String updatedByName) {

	public record Option(long minutes, String label) {
	}

}
