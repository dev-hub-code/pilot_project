package com.sealease.backend.auth.dto;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
		UUID id,
		/** The readable ID ("SL03500"). */
		String userCode,
		String email,
		String firstName,
		String lastName,
		String status,
		Set<String> roles,
		Set<String> permissions,
		boolean passwordChangeRequired) {
}
