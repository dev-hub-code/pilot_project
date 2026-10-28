package com.sealease.backend.terms.controller;

import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.terms.dto.TermsRequest;
import com.sealease.backend.terms.dto.TermsView;
import com.sealease.backend.terms.service.TermsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The terms and conditions' wording: read, and save as a new version. */
@RestController
@RequestMapping("/api/v1/admin/terms")
@PreAuthorize("hasAuthority('TERMS_MANAGE')")
public class AdminTermsController {

	private final TermsService terms;

	public AdminTermsController(TermsService terms) {
		this.terms = terms;
	}

	@GetMapping
	public TermsView view() {
		return terms.view();
	}

	/** Saves the wording as a new version, which everyone signing up from now on accepts. */
	@PutMapping
	public TermsView.Version save(AuthenticatedUser actor, @Valid @RequestBody TermsRequest request) {
		return terms.save(actor.userId(), request);
	}

}
