package com.sealease.backend.earning.controller;

import com.sealease.backend.earning.dto.PublicRentCredit;
import com.sealease.backend.earning.dto.RentCreditSettingsRequest;
import com.sealease.backend.earning.dto.RentCreditSettingsResponse;
import com.sealease.backend.earning.service.RentCreditSettingsService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** How often rent is credited: shown everywhere rent is described, changed by admins. */
@RestController
public class RentCreditSettingsController {

	private final RentCreditSettingsService settings;

	public RentCreditSettingsController(RentCreditSettingsService settings) {
		this.settings = settings;
	}

	/** For wording on the website and in the portal; public. */
	@GetMapping("/api/v1/public/rent-credit")
	public PublicRentCredit forPublic() {
		return settings.forPublic();
	}

	@GetMapping("/api/v1/admin/settings/rent-credit")
	@PreAuthorize("hasAnyAuthority('SYSTEM_SETTINGS_MANAGE', 'PAYOUT_PROCESS', 'FINANCE_VIEW')")
	public RentCreditSettingsResponse get() {
		return settings.get();
	}

	/** Applies from the next crediting run. */
	@PutMapping("/api/v1/admin/settings/rent-credit")
	@PreAuthorize("hasAnyAuthority('SYSTEM_SETTINGS_MANAGE', 'PAYOUT_PROCESS')")
	public RentCreditSettingsResponse update(AuthenticatedUser admin, @Valid @RequestBody RentCreditSettingsRequest request) {
		return settings.update(admin.userId(), request.intervalMinutes());
	}

}
