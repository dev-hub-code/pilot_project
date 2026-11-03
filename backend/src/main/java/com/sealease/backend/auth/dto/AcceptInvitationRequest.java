package com.sealease.backend.auth.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.common.validation.ValidPhone;
import com.sealease.backend.user.service.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A client opening the network investor account they were invited to. The email address is the invitation's, so it is
 * not sent; following the emailed link confirms it.
 */
@ValidPhone
public record AcceptInvitationRequest(
		@NotBlank @Size(max = 100) String token,
		@NotBlank @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.RULES_MESSAGE) String password,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank(message = "Enter your mobile number") @Size(max = 30) String phone,
		@IsoCountry String phoneCountry,
		@NotNull(message = TERMS) @AssertTrue(message = TERMS) Boolean acceptTerms) implements ValidPhone.Input {

	private static final String TERMS = "Accept the terms and conditions to create an account";

	@Override
	public String toString() {
		return "AcceptInvitationRequest[token=***]";
	}

}
