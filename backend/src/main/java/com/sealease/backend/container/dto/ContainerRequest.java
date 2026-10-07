package com.sealease.backend.container.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.common.validation.IsoCurrency;
import com.sealease.backend.container.entity.ContainerCondition;
import com.sealease.backend.container.entity.ContainerType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Container registration. On update, {@code containerNumber} and {@code containerType} must match
 * the registered values (identity is immutable).
 */
public record ContainerRequest(
		@NotBlank @Size(max = 13) String containerNumber,
		@NotNull ContainerType containerType,
		@NotNull ContainerCondition condition,
		@NotNull @DecimalMin(value = "0.01") @Digits(integer = 6, fraction = 2) BigDecimal capacityCbm,
		@NotNull @Positive Integer maxGrossKg,
		@NotNull @Positive Integer tareKg,
		@NotNull @Min(1960) @Max(2100) Integer manufactureYear,
		@Size(max = 100) String manufacturer,
		@NotBlank @Size(max = 120) String currentLocation,
		@NotBlank @IsoCountry String locationCountry,
		@DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal acquisitionCost,
		@IsoCurrency String acquisitionCurrency,
		@Size(max = 1000) String notes) {
}
