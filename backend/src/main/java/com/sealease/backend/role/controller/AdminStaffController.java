package com.sealease.backend.role.controller;

import com.sealease.backend.role.dto.CreateStaffRequest;
import com.sealease.backend.role.dto.StaffCredentialsResponse;
import com.sealease.backend.role.service.StaffAccountService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Responses carry a temporary password, so they are never cached. */
@RestController
public class AdminStaffController {

	private final StaffAccountService staff;

	public AdminStaffController(StaffAccountService staff) {
		this.staff = staff;
	}

	@PostMapping("/api/v1/admin/staff")
	@PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
	public ResponseEntity<StaffCredentialsResponse> create(AuthenticatedUser actor, @Valid @RequestBody CreateStaffRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
			.body(staff.create(actor.userId(), request));
	}

	@PostMapping("/api/v1/admin/users/{userId}/temporary-password")
	@PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
	public ResponseEntity<StaffCredentialsResponse> resetPassword(AuthenticatedUser actor, @PathVariable UUID userId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(staff.resetPassword(actor.userId(), userId));
	}

}
