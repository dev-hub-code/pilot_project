package com.sealease.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param referralCode optional: the code of the investor who referred this person; fixed forever once set */
public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 12, max = 128) String password,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@Size(max = 20) String referralCode) {

	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + "]";
	}

}
