package com.sealease.backend.investment.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.investment.dto.PlanDetail;
import com.sealease.backend.investment.dto.PlanQuote;
import com.sealease.backend.investment.dto.PlanView;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.service.PlanCatalogService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Investors see the plans for their type; staff with INVESTMENT_VIEW see what an investor of their type would. */
@RestController
@Validated
@RequestMapping("/api/v1/plans")
@PreAuthorize("hasAnyAuthority('INVESTOR_PORTAL', 'INVESTMENT_VIEW')")
public class PlanController {

	private final PlanCatalogService plans;

	public PlanController(PlanCatalogService plans) {
		this.plans = plans;
	}

	@GetMapping
	public PageResponse<PlanView> search(AuthenticatedUser viewer, @RequestParam(required = false) ProductStatus status,
			@PageableDefault(size = 24) Pageable pageable) {
		return PageResponse.from(plans.searchFor(viewer.userId(), status, pageable));
	}

	@GetMapping("/{productId}")
	public PlanDetail detail(AuthenticatedUser viewer, @PathVariable UUID productId) {
		return plans.detail(productId, viewer.userId());
	}

	@GetMapping("/{productId}/quote")
	public PlanQuote quote(AuthenticatedUser viewer, @PathVariable UUID productId,
			@RequestParam @Min(1) @Max(100000000) int units) {
		return plans.quote(productId, viewer.userId(), units);
	}

}
