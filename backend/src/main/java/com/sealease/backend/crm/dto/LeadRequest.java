package com.sealease.backend.crm.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.common.validation.IsoCurrency;
import com.sealease.backend.crm.entity.LeadInterest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A lead as staff enter or edit it. Email or phone is required.
 *
 * @param ownerId only honoured for holders of LEAD_ASSIGN; otherwise a new lead belongs to its creator
 */
public record LeadRequest(
		@NotBlank @Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@Email @Size(max = 254) String email,
		@Pattern(regexp = "^[+0-9 ()-]{6,40}$", message = "must be a phone number") String phone,
		@IsoCountry String country,
		@NotNull LeadInterest interest,
		@DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal estimatedAmount,
		@IsoCurrency String estimatedCurrency,
		Instant nextFollowUpAt,
		UUID ownerId) {
}
