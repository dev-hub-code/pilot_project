package com.sealease.backend.crm.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.common.validation.ValidPhone;
import com.sealease.backend.crm.entity.LeadInterest;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The public "talk to us" form.
 *
 * @param website honeypot: hidden from people, filled in by bots; a non-empty value is silently dropped
 * @param consent the person agreed to be contacted about investing
 */
@ValidPhone(mobile = false, message = "Enter a valid phone number for the selected country")
public record PublicLeadRequest(
		@NotBlank @Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank(message = "Enter your phone number") @Size(max = 30) String phone,
		/** The country the phone number was typed in. */
		@NotBlank(message = "Choose the phone number's country") @IsoCountry String phoneCountry,
		@NotBlank(message = "Choose your country") @IsoCountry String country,
		@NotNull LeadInterest interest,
		@Size(max = 2000) String message,
		@NotNull @AssertTrue(message = "is required to contact you") Boolean consent,
		@Size(max = 200) String website) implements ValidPhone.Input {

	@Override
	public String toString() {
		return "PublicLeadRequest[interest=" + interest + "]";
	}

}
