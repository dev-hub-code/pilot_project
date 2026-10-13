package com.sealease.backend.role.controller;

import com.sealease.backend.role.dto.CreateStaffRequest;
import com.sealease.backend.role.dto.StaffCredentialsResponse;
import com.sealease.backend.role.service.CredentialsEmailSender;
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

/**
 * Employee accounts. Responses carry a temporary password, so they are never cached. The credentials are
 * also emailed to the employee, once the account change has been saved.
 */
@RestController
public class AdminStaffController {

	private final StaffAccountService staff;
	private final CredentialsEmailSender credentialsEmail;

	public AdminStaffController(StaffAccountService staff, CredentialsEmailSender credentialsEmail) {
		this.staff = staff;
		this.credentialsEmail = credentialsEmail;
	}

	@PostMapping("/api/v1/admin/staff")
	@PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
	public ResponseEntity<StaffCredentialsResponse> create(AuthenticatedUser actor, @Valid @RequestBody CreateStaffRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
			.body(emailed(staff.create(actor.userId(), request), false));
	}

	@PostMapping("/api/v1/admin/users/{userId}/temporary-password")
	@PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
	public ResponseEntity<StaffCredentialsResponse> resetPassword(AuthenticatedUser actor, @PathVariable UUID userId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(emailed(staff.resetPassword(actor.userId(), userId), true));
	}

	/** Sent after the service's transaction committed, so no email goes out for a change that was rolled back. */
	private StaffCredentialsResponse emailed(StaffCredentialsResponse credentials, boolean reset) {
		return credentials.withEmailed(credentialsEmail.send(credentials, reset));
	}

}
