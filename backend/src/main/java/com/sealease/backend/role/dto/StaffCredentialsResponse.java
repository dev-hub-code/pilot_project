package com.sealease.backend.role.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * An employee account and its temporary password. The password is returned this once and cannot be
 * retrieved again; the holder must replace it at first sign-in, before {@code expiresAt}.
 *
 * @param emailed whether the credentials were also emailed to the employee
 */
public record StaffCredentialsResponse(UUID userId, String email, String firstName, String lastName, Set<String> roles,
		String temporaryPassword, Instant expiresAt, boolean emailed) {

	public StaffCredentialsResponse withEmailed(boolean emailed) {
		return new StaffCredentialsResponse(userId, email, firstName, lastName, roles, temporaryPassword, expiresAt, emailed);
	}

	@Override
	public String toString() {
		return "StaffCredentialsResponse[userId=" + userId + ", temporaryPassword=***]";
	}

}
