package com.sealease.backend.role.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A staff account and its temporary password. The password is returned this once and cannot be
 * retrieved again; the holder must replace it at first sign-in, before {@code expiresAt}.
 */
public record StaffCredentialsResponse(UUID userId, String email, String firstName, String lastName, Set<String> roles,
		String temporaryPassword, Instant expiresAt) {

	@Override
	public String toString() {
		return "StaffCredentialsResponse[userId=" + userId + ", temporaryPassword=***]";
	}

}
