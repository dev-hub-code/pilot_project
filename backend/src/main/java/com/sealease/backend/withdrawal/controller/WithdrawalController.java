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
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.withdrawal.dto.WithdrawalEventResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalOverview;
import com.sealease.backend.withdrawal.dto.WithdrawalQuoteResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalRespondRequest;
import com.sealease.backend.withdrawal.service.WithdrawalHistory;
import com.sealease.backend.withdrawal.service.WithdrawalOverviewService;
import com.sealease.backend.withdrawal.service.WithdrawalPaymentService;
import com.sealease.backend.withdrawal.service.WithdrawalRequestService;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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
	private final WithdrawalRequestService requests;
	private final WithdrawalOverviewService overviews;
	private final WithdrawalPaymentService payments;
	private final WithdrawalHistory history;
	private final ReferralPayoutModeService referralModes;
	private final DemoViewService demo;

	public WithdrawalController(WithdrawalService withdrawals, WithdrawalRequestService requests,
			WithdrawalOverviewService overviews, WithdrawalPaymentService payments, WithdrawalHistory history,
			ReferralPayoutModeService referralModes, DemoViewService demo) {
		this.withdrawals = withdrawals;
		this.requests = requests;
		this.overviews = overviews;
		this.payments = payments;
		this.history = history;
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

	/** The withdrawal dashboard: totals, wallets, what each investment has earned and what is locked, bank accounts. */
	@GetMapping("/overview")
	public WithdrawalOverview overview(AuthenticatedUser investor) {
		return overviews.overview(investor.userId());
	}

	/** What withdrawing an amount from a wallet would pay, with the charges and tax in force. Changes nothing. */
	@GetMapping("/quote")
	public WithdrawalQuoteResponse quote(AuthenticatedUser investor, @RequestParam EarningsBalance balance,
			@RequestParam BigDecimal amount) {
		return requests.quote(investor.userId(), balance, amount);
	}

	/**
	 * An online investor asks to withdraw an amount from a wallet, confirming with their password. Submitted for
	 * review. Requires an {@code Idempotency-Key}; a retry returns the same withdrawal.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WithdrawalResponse request(AuthenticatedUser investor,
			@RequestHeader(IdempotencyKey.HEADER) String idempotencyKey, @Valid @RequestBody WithdrawalRequest request) {
		return requests.submit(investor.userId(), request, IdempotencyKey.require(idempotencyKey));
	}

	/** One of the investor's withdrawals. */
	@GetMapping("/{withdrawalId}")
	public WithdrawalResponse one(AuthenticatedUser investor, @PathVariable UUID withdrawalId) {
		return withdrawals.mineOne(investor.userId(), withdrawalId)
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
	}

	/** Its tracking timeline: the steps meant for the investor, without staff names or internal notes. */
	@GetMapping("/{withdrawalId}/timeline")
	public List<WithdrawalEventResponse> timeline(AuthenticatedUser investor, @PathVariable UUID withdrawalId) {
		one(investor, withdrawalId);
		return history.timeline(withdrawalId, false);
	}

	/** Cancels a request still being reviewed; the amount goes back to the wallet. */
	@PostMapping("/{withdrawalId}/cancel")
	public WithdrawalResponse cancel(AuthenticatedUser investor, @PathVariable UUID withdrawalId) {
		return requests.cancel(investor.userId(), withdrawalId);
	}

	/** Answers a reviewer's request for more information. */
	@PostMapping("/{withdrawalId}/respond")
	public WithdrawalResponse respond(AuthenticatedUser investor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody WithdrawalRespondRequest request) {
		return requests.respond(investor.userId(), withdrawalId, request.message());
	}

	/** The receipt of a paid withdrawal (PDF). */
	@GetMapping("/{withdrawalId}/receipt")
	public ResponseEntity<byte[]> receipt(AuthenticatedUser investor, @PathVariable UUID withdrawalId) {
		WithdrawalPaymentService.Receipt receipt = payments.receipt(investor.userId(), withdrawalId);
		return pdf(receipt);
	}

	static ResponseEntity<byte[]> pdf(WithdrawalPaymentService.Receipt receipt) {
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(receipt.filename() + ".pdf").build().toString())
			.cacheControl(CacheControl.noStore())
			.body(receipt.content());
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
