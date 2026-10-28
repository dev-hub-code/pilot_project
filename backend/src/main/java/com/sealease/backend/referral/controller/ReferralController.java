package com.sealease.backend.referral.controller;

import com.sealease.backend.demo.service.DemoViewService;
import com.sealease.backend.referral.dto.Downline;
import com.sealease.backend.referral.dto.DownlineMember;
import com.sealease.backend.referral.dto.ReferralMonth;
import com.sealease.backend.referral.dto.ReferralOverview;
import com.sealease.backend.referral.service.ReferralEarningService;
import com.sealease.backend.referral.service.ReferralService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/referrals")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class ReferralController {

	private final ReferralService referrals;
	private final ReferralEarningService earnings;
	private final DemoViewService demo;

	public ReferralController(ReferralService referrals, ReferralEarningService earnings, DemoViewService demo) {
		this.referrals = referrals;
		this.earnings = earnings;
		this.demo = demo;
	}

	/** Code (created on first visit), rates, downline size per level and commission earned. */
	@GetMapping("/me")
	public ReferralOverview overview(AuthenticatedUser investor) {
		ReferralOverview overview = referrals.overview(investor.userId());
		return demo.isDemo(investor.userId()) ? demo.referralOverview(investor.userId(), overview) : overview;
	}

	@GetMapping("/downline")
	public Downline downline(AuthenticatedUser investor) {
		return demo.isDemo(investor.userId()) ? demo.downline(investor.userId()) : referrals.downline(investor.userId());
	}

	/** One member of the downline, by its id in the downline view. */
	@GetMapping("/members/{memberId}")
	public DownlineMember member(AuthenticatedUser investor, @PathVariable String memberId) {
		return demo.isDemo(investor.userId()) ? demo.member(investor.userId(), memberId)
				: referrals.member(investor.userId(), memberId);
	}

	/** Commission per calendar month (UTC) and level, latest month first. */
	@GetMapping("/monthly")
	public List<ReferralMonth> monthly(AuthenticatedUser investor) {
		return demo.isDemo(investor.userId()) ? demo.referralMonthly(investor.userId()) : earnings.monthly(investor.userId());
	}

}
