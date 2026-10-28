package com.sealease.backend.terms.controller;

import com.sealease.backend.terms.dto.TermsView;
import com.sealease.backend.terms.service.TermsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The terms and conditions in force, for anyone: shown on the website and accepted when signing up. */
@RestController
public class TermsController {

	private final TermsService terms;

	public TermsController(TermsService terms) {
		this.terms = terms;
	}

	@GetMapping("/api/v1/public/terms")
	public TermsView.Version current() {
		return terms.current();
	}

}
