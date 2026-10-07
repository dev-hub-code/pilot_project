package com.sealease.backend.referral.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.referral.dto.AdminReferralView;
import com.sealease.backend.referral.dto.RateVersionResponse;
import com.sealease.backend.referral.dto.ReferralEarningResponse;
import com.sealease.backend.referral.dto.ScheduleRatesRequest;
import com.sealease.backend.referral.service.ReferralEarningService;
import com.sealease.backend.referral.service.ReferralRateService;
import com.sealease.backend.referral.service.ReferralService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AdminReferralController {

	private final ReferralService referrals;
	private final ReferralRateService rates;
	private final ReferralEarningService earnings;

	public AdminReferralController(ReferralService referrals, ReferralRateService rates,
			ReferralEarningService earnings) {
		this.referrals = referrals;
		this.rates = rates;
		this.earnings = earnings;
	}

	@GetMapping("/api/v1/admin/referral-rates")
	@PreAuthorize("hasAnyAuthority('REFERRAL_CONFIG_MANAGE', 'FINANCE_VIEW')")
	public List<RateVersionResponse> rates() {
		return rates.history();
	}

	@PostMapping("/api/v1/admin/referral-rates")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('REFERRAL_CONFIG_MANAGE')")
	public RateVersionResponse schedule(AuthenticatedUser actor, @Valid @RequestBody ScheduleRatesRequest request) {
		return rates.schedule(actor.userId(), request);
	}

	@PostMapping("/api/v1/admin/referral-rates/{versionId}/cancel")
	@PreAuthorize("hasAuthority('REFERRAL_CONFIG_MANAGE')")
	public RateVersionResponse cancel(AuthenticatedUser actor, @PathVariable UUID versionId) {
		return rates.cancel(actor.userId(), versionId);
	}

	@GetMapping("/api/v1/admin/referral-earnings")
	@PreAuthorize("hasAuthority('FINANCE_VIEW')")
	public PageResponse<ReferralEarningResponse> earnings(@RequestParam(required = false) UUID beneficiaryId,
			@RequestParam(required = false) UUID sourceId,
			@PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(earnings.search(beneficiaryId, sourceId, pageable));
	}

	@GetMapping("/api/v1/admin/users/{userId}/referrals")
	@PreAuthorize("hasAuthority('USER_VIEW')")
	public AdminReferralView user(@PathVariable UUID userId) {
		return referrals.adminView(userId);
	}

}
