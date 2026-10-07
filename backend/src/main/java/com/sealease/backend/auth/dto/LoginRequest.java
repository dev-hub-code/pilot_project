package com.sealease.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password length is not policy-checked on login so that accounts predating a policy change still work. */
public record LoginRequest(
		@NotBlank @Size(max = 254) String email,
		@NotBlank @Size(max = 128) String password) {

	@Override
	public String toString() {
		return "LoginRequest[email=" + email + "]";
	}

}
