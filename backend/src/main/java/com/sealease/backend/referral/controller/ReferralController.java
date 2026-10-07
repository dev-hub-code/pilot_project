package com.sealease.backend.referral.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.referral.dto.Downline;
import com.sealease.backend.referral.dto.ReferralEarningResponse;
import com.sealease.backend.referral.dto.ReferralOverview;
import com.sealease.backend.referral.service.ReferralEarningService;
import com.sealease.backend.referral.service.ReferralService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/referrals")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class ReferralController {

	private final ReferralService referrals;
	private final ReferralEarningService earnings;

	public ReferralController(ReferralService referrals, ReferralEarningService earnings) {
		this.referrals = referrals;
		this.earnings = earnings;
	}

	/** Code (created on first visit), rates, downline size per level and commission earned. */
	@GetMapping("/me")
	public ReferralOverview overview(AuthenticatedUser investor) {
		return referrals.overview(investor.userId());
	}

	@GetMapping("/downline")
	public Downline downline(AuthenticatedUser investor) {
		return referrals.downline(investor.userId());
	}

	@GetMapping("/earnings")
	public PageResponse<ReferralEarningResponse> earnings(AuthenticatedUser investor,
			@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(earnings.mine(investor.userId(), pageable));
	}

}
