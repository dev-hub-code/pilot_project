package com.sealease.backend.role.controller;

import com.sealease.backend.role.dto.AssignRolesRequest;
import com.sealease.backend.role.dto.UserRolesResponse;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}/roles")
public class UserRoleController {

	private final UserRoleService userRoleService;

	public UserRoleController(UserRoleService userRoleService) {
		this.userRoleService = userRoleService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_VIEW')")
	public UserRolesResponse get(@PathVariable UUID userId) {
		return UserRolesResponse.of(userId, userRoleService.authoritiesOf(userId));
	}

	@PutMapping
	@PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
	public UserRolesResponse replace(AuthenticatedUser actor, @PathVariable UUID userId,
			@Valid @RequestBody AssignRolesRequest request) {
		return UserRolesResponse.of(userId, userRoleService.replaceRoles(actor.userId(), userId, request.roles()));
	}

}
