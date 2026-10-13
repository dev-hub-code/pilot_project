package com.sealease.backend.withdrawal.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.withdrawal.dto.ReferralPayoutMode;
import com.sealease.backend.withdrawal.dto.ReferralPayoutModeRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalPolicy;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.service.ReferralPayoutModeService;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Investors' withdrawals: made by the monthly schedule or at the office, never requested here. */
@RestController
@RequestMapping("/api/v1/withdrawals")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class WithdrawalController {

	private final WithdrawalService withdrawals;
	private final ReferralPayoutModeService referralModes;

	public WithdrawalController(WithdrawalService withdrawals, ReferralPayoutModeService referralModes) {
		this.withdrawals = withdrawals;
		this.referralModes = referralModes;
	}

	@GetMapping("/policy")
	public WithdrawalPolicy policy() {
		return withdrawals.policy();
	}

	@GetMapping
	public PageResponse<WithdrawalResponse> mine(AuthenticatedUser investor, @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(withdrawals.mine(investor.userId(), pageable));
	}

	/** How referral earnings are paid this year. */
	@GetMapping("/referral-mode")
	public ReferralPayoutMode referralMode(AuthenticatedUser investor) {
		return referralModes.current(investor.userId());
	}

	/** The investor's choice for this year: once a year. */
	@PutMapping("/referral-mode")
	public ReferralPayoutMode chooseReferralMode(AuthenticatedUser investor, @Valid @RequestBody ReferralPayoutModeRequest request) {
		return referralModes.choose(investor.userId(), request.mode());
	}

}
