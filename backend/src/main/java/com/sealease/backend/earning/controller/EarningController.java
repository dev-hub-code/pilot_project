package com.sealease.backend.earning.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.earning.dto.EarningsSummary;
import com.sealease.backend.earning.dto.PayoutMonth;
import com.sealease.backend.earning.dto.PayoutResponse;
import com.sealease.backend.earning.entity.PayoutStatus;
import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/** The investor's monthly payouts: rent plus capital returned, per container. */
@RestController
@RequestMapping("/api/v1/earnings")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class EarningController {

	private final PayoutService payouts;

	public EarningController(PayoutService payouts) {
		this.payouts = payouts;
	}

	@GetMapping("/summary")
	public EarningsSummary summary(AuthenticatedUser investor) {
		return payouts.summary(investor.userId());
	}

	/** Payouts per month (by due date, {@code yyyy-MM}, both ends included), paid and still scheduled. */
	@GetMapping("/monthly")
	public List<PayoutMonth> monthly(AuthenticatedUser investor,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
		return payouts.monthly(investor.userId(), from, to);
	}

	/** Paid payouts newest first by default; {@code status=SCHEDULED} lists what is coming. */
	@GetMapping
	public PageResponse<PayoutResponse> payouts(AuthenticatedUser investor,
			@RequestParam(required = false) PayoutStatus status,
			@PageableDefault(size = 20, sort = "dueOn", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(payouts.mine(investor.userId(), status, pageable));
	}

}
