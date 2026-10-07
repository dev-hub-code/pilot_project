package com.sealease.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of /refresh and /logout. */
public record RefreshTokenRequest(@NotBlank @Size(max = 128) String refreshToken) {

	@Override
	public String toString() {
		return "RefreshTokenRequest[***]";
	}

}
