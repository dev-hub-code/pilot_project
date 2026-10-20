package com.sealease.backend.investment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Replaces every band. Each runs from {@code fromAmount} to {@code toAmount} (inclusive); only the last may leave
 * {@code toAmount} out, for no upper limit. Bands follow each other with no overlap or gap.
 */
public record RentTiersRequest(@NotEmpty @Size(max = 20) List<@Valid @NotNull Tier> tiers) {

	public record Tier(
			@NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal fromAmount,
			@DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal toAmount,
			@NotNull @DecimalMin("0.01") @DecimalMax("20") @Digits(integer = 2, fraction = 2) BigDecimal monthlyRentPercent) {
	}

}
