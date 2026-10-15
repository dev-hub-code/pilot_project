package com.sealease.backend.earning.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.earning.dto.DuePayouts;
import com.sealease.backend.earning.dto.LeaseRentResponse;
import com.sealease.backend.earning.dto.LeaseRentResponse.LeaseRentStatus;
import com.sealease.backend.earning.dto.LeaseSearchCriteria;
import com.sealease.backend.earning.service.PayoutService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Investors' rent, per container lease. It is credited automatically every hour; finance can also run it now. */
@RestController
@RequestMapping("/api/v1/admin/payouts")
public class AdminPayoutController {

	private static final String VIEW = "hasAnyAuthority('FINANCE_VIEW', 'PAYOUT_PROCESS')";

	private final PayoutService payouts;

	public AdminPayoutController(PayoutService payouts) {
		this.payouts = payouts;
	}

	@GetMapping
	@PreAuthorize(VIEW)
	public PageResponse<LeaseRentResponse> search(@RequestParam(required = false) LeaseRentStatus status,
			@RequestParam(required = false) UUID userId, @RequestParam(required = false) UUID holdingId,
			@PageableDefault(size = 25, sort = "startsAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(payouts.search(new LeaseSearchCriteria(status, userId, holdingId), pageable));
	}

	/** Leases with hours that have ended and are not credited yet: normally none, crediting runs every minute. */
	@GetMapping("/due")
	@PreAuthorize(VIEW)
	public List<DuePayouts> due() {
		return payouts.due();
	}

	/** Credits every hour of rent that has ended now, instead of waiting for the next automatic run. */
	@PostMapping("/run")
	@PreAuthorize("hasAuthority('PAYOUT_PROCESS')")
	public Map<String, Integer> run() {
		return Map.of("credited", payouts.creditDue());
	}

}
