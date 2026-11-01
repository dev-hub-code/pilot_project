package com.sealease.backend.investment.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.ratelimit.FixedWindowRateLimiter;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.investment.dto.PlanView;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.service.PlanCatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

/**
 * The open API: online plans, read-only, for anyone without signing in (published at https://www.sealease.in/api/open/v1
 * by the web app). Nothing can be bought, changed or seen about investors here.
 */
@RestController
@Validated
@RequestMapping("/api/v1/open")
public class OpenPlansController {

	/** Requests per visitor per minute, across the open API. */
	static final int REQUESTS_PER_MINUTE = 120;

	private final PlanCatalogService plans;
	private final FixedWindowRateLimiter perIp;

	public OpenPlansController(PlanCatalogService plans, Clock clock) {
		this.plans = plans;
		this.perIp = new FixedWindowRateLimiter(REQUESTS_PER_MINUTE, Duration.ofMinutes(1), clock);
	}

	/** Listed online plans, open ones by default; {@code status} CLOSED for those no longer taking investments. */
	@GetMapping("/plans")
	public PageResponse<PlanView> list(@RequestParam(required = false) ProductStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		limit();
		return PageResponse.from(plans.openPlans(status, PageRequest.of(page, size)));
	}

	@GetMapping("/plans/{code}")
	public PlanView plan(@PathVariable String code) {
		limit();
		return plans.openPlan(code);
	}

	private void limit() {
		String ip = ClientInfo.current().ipAddress();
		perIp.acquire(ip == null ? "unknown" : ip);
	}

}
