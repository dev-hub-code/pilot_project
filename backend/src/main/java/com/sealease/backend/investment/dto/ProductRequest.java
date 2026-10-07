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
 * Plan terms set by staff. The monthly capital return is not set: it is 100 ÷ tenure % of the price,
 * so the whole price is returned over the lease.
 *
 * @param price              per container
 * @param monthlyRentPercent rent paid to the investor every month, as a percentage of the price
 * @param tenureMonths       how long each container is leased, and so how many monthly payouts there are
 */
public record ProductRequest(
		@NotNull ContainerType containerType,
		@NotBlank @Size(max = 140) String title,
		@NotBlank @Size(max = 400) String summary,
		@NotBlank @Size(max = 10_000) String description,
		@NotBlank @PlatformCurrency String currency,
		@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal price,
		@NotNull @DecimalMin("0.01") @DecimalMax("20") @Digits(integer = 2, fraction = 2) BigDecimal monthlyRentPercent,
		@NotNull @Min(1) @Max(120) Integer tenureMonths,
		@NotBlank @Size(max = 10_000) String riskDisclosure,
		@NotBlank @Size(max = 50_000) String termsAndConditions) {
}
