package com.sealease.backend.agreement.controller;

import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.agreement.service.LeaseAgreementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Issued lease agreements, for staff who can see orders (like invoices). */
@RestController
@RequestMapping("/api/v1/admin/agreements")
@PreAuthorize("hasAuthority('ORDER_VIEW')")
public class AdminAgreementController {

	private final LeaseAgreementService agreements;

	public AdminAgreementController(LeaseAgreementService agreements) {
		this.agreements = agreements;
	}

	@GetMapping
	public List<LeaseAgreementResponse> forOrder(@RequestParam UUID orderId) {
		return agreements.forOrder(orderId);
	}

	@GetMapping("/{holdingId}/pdf")
	public ResponseEntity<byte[]> pdf(@PathVariable UUID holdingId) {
		return agreements.pdf(holdingId, null).toResponse();
	}

}
