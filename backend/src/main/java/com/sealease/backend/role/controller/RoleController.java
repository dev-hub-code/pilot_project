package com.sealease.backend.role.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.role.dto.CreateRoleRequest;
import com.sealease.backend.role.dto.RoleResponse;
import com.sealease.backend.role.dto.UpdateRoleRequest;
import com.sealease.backend.role.service.RoleService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/roles")
public class RoleController {

	private final RoleService roleService;

	public RoleController(RoleService roleService) {
		this.roleService = roleService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_VIEW')")
	public PageResponse<RoleResponse> list(@PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
		return PageResponse.from(roleService.list(pageable));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_VIEW')")
	public RoleResponse get(@PathVariable UUID id) {
		return roleService.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	public RoleResponse create(AuthenticatedUser actor, @Valid @RequestBody CreateRoleRequest request) {
		return roleService.create(actor.userId(), request);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	public RoleResponse update(AuthenticatedUser actor, @PathVariable UUID id,
			@Valid @RequestBody UpdateRoleRequest request) {
		return roleService.update(actor.userId(), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	public void delete(AuthenticatedUser actor, @PathVariable UUID id) {
		roleService.delete(actor.userId(), id);
	}

}
