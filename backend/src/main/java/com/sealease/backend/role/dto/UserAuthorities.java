package com.sealease.backend.role.dto;

import java.util.Set;
import java.util.TreeSet;

/** Effective authorities of a user: role names and the union of their permissions. */
public record UserAuthorities(Set<String> roles, Set<String> permissions) {

	public UserAuthorities {
		roles = new TreeSet<>(roles);
		permissions = new TreeSet<>(permissions);
	}

}
