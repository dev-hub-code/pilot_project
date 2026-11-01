package com.sealease.backend.investment.dto;

import com.sealease.backend.common.validation.PlatformCurrency;
import com.sealease.backend.referral.dto.ReferralScheme;
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
 * Plan terms set by staff.
 *
 * @param audience             the investor type the plan is offered to
 * @param tenureYears          the plan's tenure, in whole years
 * @param annualReturnPercent  returns a year, as a percentage of the investment; paid monthly
 * @param minUnits             the fewest units an investor may buy
 */
public record ProductRequest(
		@NotNull ReferralScheme audience,
		@NotBlank @Size(max = 140) String title,
		@NotBlank @Size(max = 400) String summary,
		@NotBlank @PlatformCurrency String currency,
		@NotNull @Min(1) @Max(30) Integer tenureYears,
		@NotNull @DecimalMin("0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal annualReturnPercent,
		@NotNull @Min(1) @Max(1000000) Integer minUnits) {
}
