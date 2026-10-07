package com.sealease.backend.permission.dto;

import com.sealease.backend.permission.entity.Permission;

public record PermissionResponse(String code, String category, String description) {

	public static PermissionResponse from(Permission permission) {
		return new PermissionResponse(permission.getCode(), permission.getCategory(), permission.getDescription());
	}

}
