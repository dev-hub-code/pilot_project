package com.sealease.backend.auth.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.common.validation.ValidPhone;
import com.sealease.backend.user.service.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param phone        the mobile number, required and unique across accounts; kept on the investor's profile
 * @param phoneCountry the country the number was typed in (ISO 3166-1 alpha-2), India when not given; a number
 *                     typed with "+" and its country code may belong to another
 * @param referralCode optional: the code of the investor who referred this person, exactly as issued (8 capital letters
 *                     and digits); fixed forever once set
 * @param acceptTerms  the person accepts the terms and conditions in force; an account cannot be made without
 */
@ValidPhone
public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.RULES_MESSAGE) String password,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank(message = "Enter your mobile number") @Size(max = 30) String phone,
		@IsoCountry String phoneCountry,
		@Pattern(regexp = "^([2-9A-HJ-NP-Z]{8})?$", message = "must be 8 capital letters and digits")
		String referralCode,
		@NotNull(message = TERMS) @AssertTrue(message = TERMS) Boolean acceptTerms)
		implements ValidPhone.Input {

	private static final String TERMS = "Accept the terms and conditions to create an account";

	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + "]";
	}

}
