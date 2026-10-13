package com.sealease.backend.marketplace.controller;

import com.sealease.backend.marketplace.dto.PublicPlan;
import com.sealease.backend.marketplace.service.MarketplaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated: the open plans shown on the website. */
@RestController
public class PublicPlansController {

	/** As many as the website shows. */
	private static final int LIMIT = 6;

	private final MarketplaceService marketplace;

	public PublicPlansController(MarketplaceService marketplace) {
		this.marketplace = marketplace;
	}

	@GetMapping("/api/v1/public/plans")
	public List<PublicPlan> plans() {
		return marketplace.publicPlans(LIMIT);
	}

}
