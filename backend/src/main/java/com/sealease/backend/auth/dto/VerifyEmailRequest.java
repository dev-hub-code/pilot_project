package com.sealease.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param token the value of the {@code token} parameter in the emailed link */
public record VerifyEmailRequest(@NotBlank @Size(max = 100) String token) {

	@Override
	public String toString() {
		return "VerifyEmailRequest[token=***]";
	}

}
