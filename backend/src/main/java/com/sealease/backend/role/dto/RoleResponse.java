package com.sealease.backend.role.dto;

import com.sealease.backend.permission.entity.Permission;
import com.sealease.backend.role.entity.Role;

import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, String description, boolean system, List<String> permissions,
		long version) {

	public static RoleResponse from(Role role) {
		List<String> codes = role.getPermissions().stream().map(Permission::getCode).sorted().toList();
		return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.isSystem(), codes,
				role.getVersion());
	}

}
