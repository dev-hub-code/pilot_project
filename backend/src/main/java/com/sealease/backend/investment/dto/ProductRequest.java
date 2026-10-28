package com.sealease.backend.investment.dto;

import com.sealease.backend.common.validation.PlatformCurrency;
import com.sealease.backend.container.entity.ContainerType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Plan terms set by staff. Plans pay rent only; there is no capital return to set, and the rent rate follows from
 * each order's total (the rent rate tiers), not from the plan.
 *
 * @param price              per container
 * @param minTenureYears     the shortest lease an investor may choose, in whole years
 * @param maxTenureYears     the longest lease an investor may choose, in whole years (at least the minimum)
 */
public record ProductRequest(
		@NotNull ContainerType containerType,
		@Size(max = 140) String title,
		@NotBlank @Size(max = 400) String summary,
		@NotBlank @Size(max = 10_000) String description,
		@NotBlank @PlatformCurrency String currency,
		@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal price,
		@NotNull @Min(1) @Max(30) Integer minTenureYears,
		@NotNull @Min(1) @Max(30) Integer maxTenureYears) {
}
