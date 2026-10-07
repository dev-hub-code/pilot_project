package com.sealease.backend.investment.controller;

import com.sealease.backend.investment.dto.PortfolioResponse;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolio")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class PortfolioController {

	private final HoldingService holdings;

	public PortfolioController(HoldingService holdings) {
		this.holdings = holdings;
	}

	@GetMapping
	public PortfolioResponse portfolio(AuthenticatedUser investor) {
		return holdings.portfolio(investor.userId());
	}

}
