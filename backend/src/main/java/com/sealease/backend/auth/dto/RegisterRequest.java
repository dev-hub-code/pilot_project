package com.sealease.backend.auth.dto;

import com.sealease.backend.user.service.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param phone        the mobile number, required and unique across accounts; kept on the investor's profile
 * @param referralCode optional: the code of the investor who referred this person, exactly as issued (8 capital letters
 *                     and digits); fixed forever once set
 */
public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.RULES_MESSAGE) String password,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank(message = "Enter your mobile number")
		@Pattern(regexp = "^[+0-9 ()-]{6,20}$", message = "must be a phone number (digits, spaces, +, brackets or dashes)")
		String phone,
		@Pattern(regexp = "^([2-9A-HJ-NP-Z]{8})?$", message = "must be 8 capital letters and digits")
		String referralCode) {

	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + "]";
	}

}
