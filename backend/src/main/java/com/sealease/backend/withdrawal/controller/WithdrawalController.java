package com.sealease.backend.withdrawal.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.demo.service.DemoViewService;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.withdrawal.dto.ReferralPayoutMode;
import com.sealease.backend.withdrawal.dto.ReferralPayoutModeRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalPolicy;
import com.sealease.backend.withdrawal.dto.WithdrawalRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.service.ReferralPayoutModeService;
import com.sealease.backend.withdrawal.service.WithdrawalRunService;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Investors' withdrawals: made by the runs or at the office, and requested here by online investors. */
@RestController
@RequestMapping("/api/v1/withdrawals")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class WithdrawalController {

	private final WithdrawalService withdrawals;
	private final WithdrawalRunService runs;
	private final ReferralPayoutModeService referralModes;
	private final DemoViewService demo;

	public WithdrawalController(WithdrawalService withdrawals, WithdrawalRunService runs,
			ReferralPayoutModeService referralModes, DemoViewService demo) {
		this.withdrawals = withdrawals;
		this.runs = runs;
		this.referralModes = referralModes;
		this.demo = demo;
	}

	@GetMapping("/policy")
	public WithdrawalPolicy policy() {
		return withdrawals.policy();
	}

	@GetMapping
	public PageResponse<WithdrawalResponse> mine(AuthenticatedUser investor, @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(demo.isDemo(investor.userId()) ? demo.withdrawals(investor.userId(), pageable)
				: withdrawals.mine(investor.userId(), pageable));
	}

	/** An online investor withdraws a whole wallet. Requires an {@code Idempotency-Key}; a retry changes nothing. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WithdrawalResponse request(AuthenticatedUser investor,
			@RequestHeader(IdempotencyKey.HEADER) String idempotencyKey, @Valid @RequestBody WithdrawalRequest request) {
		return runs.request(investor.userId(), request.balance(), IdempotencyKey.require(idempotencyKey));
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
