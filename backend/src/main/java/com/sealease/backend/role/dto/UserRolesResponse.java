package com.sealease.backend.role.dto;

import java.util.Set;
import java.util.UUID;

public record UserRolesResponse(UUID userId, Set<String> roles, Set<String> permissions) {

	public static UserRolesResponse of(UUID userId, UserAuthorities authorities) {
		return new UserRolesResponse(userId, authorities.roles(), authorities.permissions());
	}

}
