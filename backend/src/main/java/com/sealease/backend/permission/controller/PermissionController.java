package com.sealease.backend.permission.controller;

import com.sealease.backend.permission.dto.PermissionResponse;
import com.sealease.backend.permission.service.PermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/permissions")
public class PermissionController {

	private final PermissionService permissionService;

	public PermissionController(PermissionService permissionService) {
		this.permissionService = permissionService;
	}

	/** The full catalogue is small (tens of rows) and bounded by code, so it is not paginated. */
	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_VIEW')")
	public List<PermissionResponse> list() {
		return permissionService.listAll();
	}

}
