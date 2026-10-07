package com.sealease.backend.crm.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.crm.entity.LeadInterest;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The public "talk to us" form.
 *
 * @param website honeypot: hidden from people, filled in by bots; a non-empty value is silently dropped
 * @param consent the person agreed to be contacted about investing
 */
public record PublicLeadRequest(
		@NotBlank @Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@NotBlank @Email @Size(max = 254) String email,
		@Pattern(regexp = "^[+0-9 ()-]{6,40}$", message = "must be a phone number") String phone,
		@IsoCountry String country,
		@NotNull LeadInterest interest,
		@Size(max = 2000) String message,
		@NotNull @AssertTrue(message = "is required to contact you") Boolean consent,
		@Size(max = 200) String website) {

	@Override
	public String toString() {
		return "PublicLeadRequest[interest=" + interest + "]";
	}

}
