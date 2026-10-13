package com.sealease.backend.earning.controller;

import com.sealease.backend.earning.dto.PayoutSettingsRequest;
import com.sealease.backend.earning.dto.PayoutSettingsResponse;
import com.sealease.backend.earning.service.PayoutSettingsService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The day of the month investor payouts are made. */
@RestController
@RequestMapping("/api/v1/admin/settings/payouts")
public class AdminPayoutSettingsController {

	private final PayoutSettingsService settings;

	public AdminPayoutSettingsController(PayoutSettingsService settings) {
		this.settings = settings;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('SYSTEM_SETTINGS_MANAGE', 'FINANCE_VIEW', 'PAYOUT_PROCESS')")
	public PayoutSettingsResponse get() {
		return settings.get();
	}

	/** Applies to containers bought from now on; schedules already made keep their dates. */
	@PutMapping
	@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_MANAGE')")
	public PayoutSettingsResponse update(AuthenticatedUser admin, @Valid @RequestBody PayoutSettingsRequest request) {
		return settings.update(admin.userId(), request.payoutDay());
	}

}
