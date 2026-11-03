package com.sealease.backend.auth.controller;

import com.sealease.backend.auth.dto.CreateInvestorRequest;
import com.sealease.backend.auth.dto.InvitationResponse;
import com.sealease.backend.auth.dto.InviteInvestorRequest;
import com.sealease.backend.auth.service.InvestorInvitationService;
import com.sealease.backend.auth.service.InvitationEmailSender;
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

/** Network investors: made by administrators, or invited by any employee. */
@RestController
public class AdminInvestorController {

	private final NetworkInvestorService investors;
	private final CredentialsEmailSender credentialsEmail;
	private final InvestorInvitationService invitations;
	private final InvitationEmailSender invitationEmail;

	public AdminInvestorController(NetworkInvestorService investors, CredentialsEmailSender credentialsEmail,
			InvestorInvitationService invitations, InvitationEmailSender invitationEmail) {
		this.investors = investors;
		this.credentialsEmail = credentialsEmail;
		this.invitations = invitations;
		this.invitationEmail = invitationEmail;
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

	/** Any employee (any account that is not an investor) can invite a client to open a network investor account. */
	@PostMapping("/api/v1/admin/investor-invitations")
	@PreAuthorize("isAuthenticated() and !hasAuthority('INVESTOR_PORTAL')")
	public ResponseEntity<InvitationResponse> invite(AuthenticatedUser actor,
			@Valid @RequestBody InviteInvestorRequest request) {
		InvestorInvitationService.IssuedInvitation issued = invitations.invite(actor.userId(), request);
		// Sent after the service's transaction committed, so no link goes out for an invitation that was rolled back.
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new InvitationResponse(issued.id(), issued.email(), issued.expiresAt(), invitationEmail.send(issued)));
	}

}
