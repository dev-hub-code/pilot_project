package com.sealease.backend.investment.controller;

import com.sealease.backend.investment.dto.RentTiersRequest;
import com.sealease.backend.investment.dto.RentTiersResponse;
import com.sealease.backend.investment.service.RentRateService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Monthly rent by order value: shown to investors, changed by staff who manage plans. */
@RestController
public class RentRateController {

	private final RentRateService rates;

	public RentRateController(RentRateService rates) {
		this.rates = rates;
	}

	/** For the website and the marketplace; public. */
	@GetMapping("/api/v1/public/rent-tiers")
	public RentTiersResponse forPublic() {
		RentTiersResponse all = rates.get();
		return new RentTiersResponse(all.tiers(), null, null);
	}

	@GetMapping("/api/v1/admin/settings/rent-tiers")
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public RentTiersResponse get() {
		return rates.get();
	}

	/** Applies to orders placed from now on. */
	@PutMapping("/api/v1/admin/settings/rent-tiers")
	@PreAuthorize("hasAuthority('INVESTMENT_UPDATE')")
	public RentTiersResponse update(AuthenticatedUser admin, @Valid @RequestBody RentTiersRequest request) {
		return rates.update(admin.userId(), request);
	}

}
