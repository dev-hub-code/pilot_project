package com.sealease.backend.referral.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * New commission rates for levels 1-4, in percent of the referred investor's investment,
 * paid every month of the tenure.
 *
 * @param effectiveFrom when they take effect; omitted means immediately. Never in the past.
 */
public record ScheduleRatesRequest(
		Instant effectiveFrom,
		@NotNull @Size(min = 4, max = 4)
		List<@NotNull @DecimalMin("0") @DecimalMax("10") @Digits(integer = 2, fraction = 3) BigDecimal> percents,
		@NotBlank @Size(max = 500) String reason) {
}
