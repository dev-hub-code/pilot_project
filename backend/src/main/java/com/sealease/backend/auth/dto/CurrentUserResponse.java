package com.sealease.backend.auth.dto;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
		UUID id,
		String email,
		String firstName,
		String lastName,
		String status,
		Set<String> roles,
		Set<String> permissions) {
}
