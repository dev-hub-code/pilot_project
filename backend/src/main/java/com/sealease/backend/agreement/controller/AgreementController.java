package com.sealease.backend.agreement.controller;

import com.sealease.backend.agreement.dto.LeaseAgreementDocument;
import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.agreement.service.LeaseAgreementService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * An investor's own lease agreements, one per container assigned to them. Accepting one (which starts the lease) is
 * {@code POST /api/v1/agreements/{holdingId}/accept}, in the order module.
 */
@RestController
@RequestMapping("/api/v1/agreements")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class AgreementController {

	private final LeaseAgreementService agreements;

	public AgreementController(LeaseAgreementService agreements) {
		this.agreements = agreements;
	}

	@GetMapping
	public List<LeaseAgreementResponse> mine(AuthenticatedUser investor) {
		return agreements.mine(investor.userId());
	}

	/** The agreement with its wording, to read before accepting it. */
	@GetMapping("/{holdingId}")
	public LeaseAgreementDocument document(AuthenticatedUser investor, @PathVariable UUID holdingId) {
		return agreements.document(holdingId, investor.userId());
	}

	@GetMapping("/{holdingId}/pdf")
	public ResponseEntity<byte[]> pdf(AuthenticatedUser investor, @PathVariable UUID holdingId) {
		return agreements.pdf(holdingId, investor.userId()).toResponse();
	}

}
