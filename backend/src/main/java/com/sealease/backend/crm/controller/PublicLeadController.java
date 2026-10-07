package com.sealease.backend.crm.controller;

import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.crm.dto.PublicLeadRequest;
import com.sealease.backend.crm.service.PublicLeadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated: the website's interest form. Always 202, whatever happened to the data. */
@RestController
public class PublicLeadController {

	private final PublicLeadService leads;

	public PublicLeadController(PublicLeadService leads) {
		this.leads = leads;
	}

	@PostMapping("/api/v1/public/leads")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void submit(@Valid @RequestBody PublicLeadRequest request) {
		leads.submit(request, ClientInfo.current());
	}

}
