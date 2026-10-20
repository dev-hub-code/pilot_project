package com.sealease.backend.order.controller;

import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.order.service.AgreementAcceptanceService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The investor accepts the lease agreement of a container assigned to them, which starts the lease and the rent. */
@RestController
@RequestMapping("/api/v1/agreements")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class AgreementAcceptanceController {

	private final AgreementAcceptanceService acceptances;

	public AgreementAcceptanceController(AgreementAcceptanceService acceptances) {
		this.acceptances = acceptances;
	}

	@PostMapping("/{holdingId}/accept")
	public LeaseAgreementResponse accept(AuthenticatedUser investor, @PathVariable UUID holdingId) {
		return acceptances.accept(investor.userId(), holdingId);
	}

}
