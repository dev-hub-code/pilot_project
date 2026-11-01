package com.sealease.backend.investment.controller;

import com.sealease.backend.investment.dto.PlanView;
import com.sealease.backend.investment.service.PlanCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated: the open plans shown on the website. */
@RestController
public class PublicPlansController {

	/** As many as the website shows. */
	private static final int LIMIT = 12;

	private final PlanCatalogService plans;

	public PublicPlansController(PlanCatalogService plans) {
		this.plans = plans;
	}

	@GetMapping("/api/v1/public/plans")
	public List<PlanView> plans() {
		return plans.publicPlans(LIMIT);
	}

}
