package com.sealease.backend.investment.controller;

import com.sealease.backend.demo.service.DemoViewService;
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
	private final DemoViewService demo;

	public PortfolioController(HoldingService holdings, DemoViewService demo) {
		this.holdings = holdings;
		this.demo = demo;
	}

	@GetMapping
	public PortfolioResponse portfolio(AuthenticatedUser investor) {
		return demo.isDemo(investor.userId()) ? demo.portfolio(investor.userId()) : holdings.portfolio(investor.userId());
	}

}
