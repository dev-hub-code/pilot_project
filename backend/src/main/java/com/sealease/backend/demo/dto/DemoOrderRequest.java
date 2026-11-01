package com.sealease.backend.demo.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * A dummy order of a real plan, open or closed: its title, monthly return and tenure are taken from it.
 *
 * @param memberId    a downline member's order (earning the investor commission); {@code null} for the investor's own
 * @param startsOn    the lease starts that day, within the account's time frame
 */
public record DemoOrderRequest(
		UUID memberId,
		@NotNull UUID productId,
		@NotNull @DecimalMin("1") @DecimalMax("1000000000") java.math.BigDecimal amount,
		@NotNull LocalDate startsOn) {
}
