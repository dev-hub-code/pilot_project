package com.sealease.backend.auth.controller;

import com.sealease.backend.auth.dto.CreateInvestorRequest;
import com.sealease.backend.auth.service.NetworkInvestorService;
import com.sealease.backend.role.dto.StaffCredentialsResponse;
import com.sealease.backend.role.service.CredentialsEmailSender;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Network investors, made by administrators. */
@RestController
public class AdminInvestorController {

	private final NetworkInvestorService investors;
	private final CredentialsEmailSender credentialsEmail;

	public AdminInvestorController(NetworkInvestorService investors, CredentialsEmailSender credentialsEmail) {
		this.investors = investors;
		this.credentialsEmail = credentialsEmail;
	}

	@PostMapping("/api/v1/admin/investors")
	@PreAuthorize("hasAuthority('INVESTOR_ACCOUNT_CREATE')")
	public ResponseEntity<StaffCredentialsResponse> create(AuthenticatedUser actor,
			@Valid @RequestBody CreateInvestorRequest request) {
		StaffCredentialsResponse created = investors.create(actor.userId(), request);
		// Sent after the service's transaction committed, so no email goes out for an account that was rolled back.
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
			.body(created.withEmailed(credentialsEmail.sendNewInvestor(created)));
	}

}
