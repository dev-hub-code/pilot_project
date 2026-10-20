package com.sealease.backend.crm.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.common.validation.PlatformCurrency;
import com.sealease.backend.common.validation.ValidPhone;
import com.sealease.backend.crm.entity.LeadInterest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A lead as staff enter or edit it. Email or phone is required.
 *
 * @param ownerId only honoured for holders of LEAD_ASSIGN; otherwise a new lead belongs to its creator
 */
@ValidPhone(mobile = false, message = "Enter a valid phone number for the selected country")
public record LeadRequest(
		@NotBlank @Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@Email @Size(max = 254) String email,
		@Size(max = 30) String phone,
		/** The country the phone number was typed in; India when not given. */
		@IsoCountry String phoneCountry,
		@IsoCountry String country,
		@NotNull LeadInterest interest,
		@DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal estimatedAmount,
		@PlatformCurrency String estimatedCurrency,
		Instant nextFollowUpAt,
		UUID ownerId) implements ValidPhone.Input {
}
