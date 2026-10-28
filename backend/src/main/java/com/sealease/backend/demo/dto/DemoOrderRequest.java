package com.sealease.backend.demo.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A dummy order of a real plan, open or closed: its title, container type and price per container are taken from it.
 *
 * @param memberId    a downline member's order (earning the investor commission); {@code null} for the investor's own
 * Its monthly rent is the rate of the band (rent tiers) its total falls in.
 *
 * @param tenureYears within the plan's range
 * @param startsOn    the lease starts that day, within the account's time frame
 */
public record DemoOrderRequest(
		UUID memberId,
		@NotNull UUID productId,
		@Min(1) @Max(1000) int containers,
		@Min(1) @Max(50) int tenureYears,
		@NotNull LocalDate startsOn) {
}
