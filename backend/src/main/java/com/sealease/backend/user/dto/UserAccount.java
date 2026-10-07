package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.User;
import com.sealease.backend.user.entity.UserStatus;

import java.util.UUID;

/** Read-only view of an account for other modules; the entity never leaves the user module. */
/** @param mustChangePassword the account signs in with a temporary password and must replace it first */
public record UserAccount(UUID id, String email, String firstName, String lastName, UserStatus status,
		boolean mustChangePassword) {

	public static UserAccount from(User user) {
		return new UserAccount(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
				user.getStatus(), user.isMustChangePassword());
	}

}
