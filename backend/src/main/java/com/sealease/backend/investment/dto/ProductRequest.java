package com.sealease.backend.investment.dto;

import com.sealease.backend.common.validation.IsoCurrency;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.RentalFrequency;
import com.sealease.backend.investment.entity.RiskLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Offering terms. For HNI (standalone) offerings, minimum and increment may be omitted: they are
 * always the full price.
 *
 * @param managementFeePercent share of each rental payment kept by the platform; omitted means none
 */
public record ProductRequest(
		@NotNull UUID containerId,
		@NotNull InvestmentType investmentType,
		@NotBlank @Size(max = 140) String title,
		@NotBlank @Size(max = 400) String summary,
		@NotBlank @Size(max = 10_000) String description,
		@NotBlank @IsoCurrency String currency,
		@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal totalAmount,
		@DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal minimumInvestment,
		@DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal investmentIncrement,
		@DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal maximumPerInvestor,
		@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal expectedRentalAmount,
		@NotNull RentalFrequency rentalFrequency,
		@NotNull @Min(1) @Max(360) Integer durationMonths,
		@DecimalMin("0") @DecimalMax("50") @Digits(integer = 2, fraction = 2) BigDecimal managementFeePercent,
		@Size(max = 140) String lesseeName,
		@NotNull RiskLevel riskLevel,
		@NotBlank @Size(max = 10_000) String riskDisclosure,
		@NotBlank @Size(max = 50_000) String termsAndConditions,
		@NotBlank @Size(max = 20) String termsVersion,
		Instant offerOpensAt,
		Instant offerClosesAt) {
}
