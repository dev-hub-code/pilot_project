package com.sealease.backend.agreement.controller;

import com.sealease.backend.agreement.dto.AgreementPreview;
import com.sealease.backend.agreement.dto.AgreementTemplateRequest;
import com.sealease.backend.agreement.dto.AgreementTemplateView;
import com.sealease.backend.agreement.service.AgreementTemplateService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The lease agreement's wording: read, preview, and save as a new version. */
@RestController
@RequestMapping("/api/v1/admin/agreement-template")
@PreAuthorize("hasAuthority('AGREEMENT_TEMPLATE_MANAGE')")
public class AdminAgreementTemplateController {

	private final AgreementTemplateService templates;

	public AdminAgreementTemplateController(AgreementTemplateService templates) {
		this.templates = templates;
	}

	@GetMapping
	public AgreementTemplateView view() {
		return templates.view();
	}

	/** Saves the wording as a new version, used for agreements issued from now on. */
	@PutMapping
	public AgreementTemplateView.Version save(AuthenticatedUser actor, @Valid @RequestBody AgreementTemplateRequest request) {
		return templates.save(actor.userId(), request);
	}

	/** The wording filled in with example values; nothing is saved. */
	@PostMapping("/preview")
	public AgreementPreview preview(@Valid @RequestBody AgreementTemplateRequest request) {
		return templates.preview(request);
	}

	/** The wording in force as a PDF, filled in with example values. */
	@GetMapping("/sample")
	public ResponseEntity<byte[]> sample() {
		return templates.samplePdf().toResponse();
	}

}
