package com.sealease.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param code the 6-digit code from the verification email */
public record VerifyEmailCodeRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Pattern(regexp = "\\s*\\d{6}\\s*", message = "Enter the 6-digit code") String code) {

	@Override
	public String toString() {
		return "VerifyEmailCodeRequest[email=" + email + ", code=***]";
	}

}
