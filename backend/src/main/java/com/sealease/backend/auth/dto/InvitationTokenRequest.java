package com.sealease.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token from an invitation link, sent in the body so it stays out of access logs. */
public record InvitationTokenRequest(@NotBlank @Size(max = 100) String token) {

	@Override
	public String toString() {
		return "InvitationTokenRequest[token=***]";
	}

}
