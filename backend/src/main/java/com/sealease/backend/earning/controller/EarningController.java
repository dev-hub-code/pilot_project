package com.sealease.backend.earning.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.earning.dto.EarningResponse;
import com.sealease.backend.earning.dto.EarningsSummary;
import com.sealease.backend.earning.service.EarningService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/earnings")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class EarningController {

	private final EarningService earnings;

	public EarningController(EarningService earnings) {
		this.earnings = earnings;
	}

	@GetMapping("/summary")
	public EarningsSummary summary(AuthenticatedUser investor) {
		return earnings.summary(investor.userId());
	}

	@GetMapping
	public PageResponse<EarningResponse> history(AuthenticatedUser investor, @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(earnings.history(investor.userId(), pageable));
	}

}
