package com.sealease.backend.earning.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.earning.dto.DuePayouts;
import com.sealease.backend.earning.dto.PayoutResponse;
import com.sealease.backend.earning.dto.PayoutSearchCriteria;
import com.sealease.backend.earning.entity.PayoutStatus;
import com.sealease.backend.earning.service.PayoutService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Monthly investor payouts. They are paid automatically as they fall due; finance can also run them now. */
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
	public PageResponse<PayoutResponse> search(@RequestParam(required = false) PayoutStatus status,
			@RequestParam(required = false) UUID userId, @RequestParam(required = false) UUID holdingId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBy,
			@PageableDefault(size = 25, sort = "dueOn", direction = Sort.Direction.ASC) Pageable pageable) {
		return PageResponse.from(payouts.search(new PayoutSearchCriteria(status, userId, holdingId, dueBy), pageable));
	}

	/** Payouts that have fallen due and are not paid yet. */
	@GetMapping("/due")
	@PreAuthorize(VIEW)
	public List<DuePayouts> due() {
		return payouts.due();
	}

	/** Pays everything that has fallen due now, instead of waiting for the next automatic run. */
	@PostMapping("/run")
	@PreAuthorize("hasAuthority('PAYOUT_PROCESS')")
	public Map<String, Integer> run() {
		return Map.of("paid", payouts.payDue());
	}

}
