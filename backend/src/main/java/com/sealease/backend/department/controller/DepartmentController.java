package com.sealease.backend.department.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.department.dto.DepartmentDtos.AssignStaffRequest;
import com.sealease.backend.department.dto.DepartmentDtos.CreateDepartmentRequest;
import com.sealease.backend.department.dto.DepartmentDtos.DepartmentDetail;
import com.sealease.backend.department.dto.DepartmentDtos.DepartmentSummary;
import com.sealease.backend.department.dto.DepartmentDtos.SetHeadRequest;
import com.sealease.backend.department.dto.DepartmentDtos.StaffMember;
import com.sealease.backend.department.dto.DepartmentDtos.UpdateDepartmentRequest;
import com.sealease.backend.department.service.DepartmentService;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.user.dto.AdminUserSummary;
import com.sealease.backend.user.dto.UserKind;
import com.sealease.backend.user.dto.UserSearchCriteria;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DepartmentController {

	private static final String BASE = "/api/v1/admin/departments";

	private final DepartmentService departments;
	private final UserAdministrationService users;

	public DepartmentController(DepartmentService departments, UserAdministrationService users) {
		this.departments = departments;
		this.users = users;
	}

	@GetMapping(BASE)
	@PreAuthorize("hasAnyAuthority('DEPARTMENT_VIEW', 'DEPARTMENT_MANAGE')")
	public List<DepartmentSummary> list(AuthenticatedUser viewer) {
		return departments.list(viewer);
	}

	@GetMapping(BASE + "/{id}")
	@PreAuthorize("hasAnyAuthority('DEPARTMENT_VIEW', 'DEPARTMENT_MANAGE')")
	public DepartmentDetail get(AuthenticatedUser viewer, @PathVariable UUID id) {
		return departments.get(viewer, id);
	}

	@PostMapping(BASE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
	public DepartmentSummary create(AuthenticatedUser actor, @Valid @RequestBody CreateDepartmentRequest request) {
		return departments.create(actor.userId(), request);
	}

	@PutMapping(BASE + "/{id}")
	@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
	public DepartmentSummary update(AuthenticatedUser actor, @PathVariable UUID id,
			@Valid @RequestBody UpdateDepartmentRequest request) {
		return departments.update(actor.userId(), id, request);
	}

	@PostMapping(BASE + "/{id}/activate")
	@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
	public DepartmentSummary activate(AuthenticatedUser actor, @PathVariable UUID id) {
		return departments.setActive(actor.userId(), id, true);
	}

	@PostMapping(BASE + "/{id}/deactivate")
	@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
	public DepartmentSummary deactivate(AuthenticatedUser actor, @PathVariable UUID id) {
		return departments.setActive(actor.userId(), id, false);
	}

	@PutMapping(BASE + "/{id}/head")
	@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
	public DepartmentSummary setHead(AuthenticatedUser actor, @PathVariable UUID id, @RequestBody SetHeadRequest request) {
		return departments.setHead(actor.userId(), id, request.userId());
	}

	@PostMapping(BASE + "/{id}/employees")
	@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
	public DepartmentSummary assign(AuthenticatedUser actor, @PathVariable UUID id,
			@Valid @RequestBody AssignStaffRequest request) {
		return departments.assign(actor.userId(), id, request);
	}

	@DeleteMapping(BASE + "/{id}/employees/{userId}")
	@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
	public DepartmentSummary unassign(AuthenticatedUser actor, @PathVariable UUID id, @PathVariable UUID userId) {
		return departments.unassign(actor.userId(), id, userId);
	}

	@GetMapping("/api/v1/admin/staff")
	@PreAuthorize("hasAuthority('STAFF_ACCOUNT_VIEW')")
	public List<StaffMember> staff() {
		return departments.staff();
	}

	/** Employees only, so this needs no permission to see investors. */
	@GetMapping("/api/v1/admin/staff/search")
	@PreAuthorize("hasAuthority('STAFF_ACCOUNT_VIEW')")
	public PageResponse<AdminUserSummary> searchStaff(@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) UserStatus status,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(users.search(new UserSearchCriteria(q, status, null, null, UserKind.EMPLOYEE, false), pageable));
	}

	@GetMapping("/api/v1/admin/staff/{userId}")
	@PreAuthorize("hasAuthority('STAFF_ACCOUNT_VIEW')")
	public StaffMember staffMember(@PathVariable UUID userId) {
		return departments.staffMember(userId);
	}

}
