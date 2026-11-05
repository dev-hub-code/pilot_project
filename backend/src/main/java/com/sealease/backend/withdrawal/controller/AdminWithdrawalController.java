package com.sealease.backend.withdrawal.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.withdrawal.dto.BatchDetail;
import com.sealease.backend.withdrawal.dto.BatchFileResponse;
import com.sealease.backend.withdrawal.dto.BatchResponse;
import com.sealease.backend.withdrawal.dto.ReferralPayoutMode;
import com.sealease.backend.withdrawal.dto.ReferralPayoutModeRequest;
import com.sealease.backend.withdrawal.dto.ResolveFailedRequest;
import com.sealease.backend.withdrawal.dto.InitiatePaymentRequest;
import com.sealease.backend.withdrawal.dto.InitiatePaymentResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalAdminDetail;
import com.sealease.backend.withdrawal.dto.WithdrawalDashboard;
import com.sealease.backend.withdrawal.dto.WithdrawalDecisionRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalNoteResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.entity.WithdrawalOrigin;
import com.sealease.backend.withdrawal.service.WithdrawalPaymentService;
import com.sealease.backend.withdrawal.service.WithdrawalReviewService;
import com.sealease.backend.common.time.DisplayTimeZone;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.sealease.backend.withdrawal.dto.WithdrawalRunResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalSearchCriteria;
import com.sealease.backend.withdrawal.dto.WithdrawalSettingsRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalSettingsResponse;
import com.sealease.backend.withdrawal.entity.BatchStatus;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.service.PayoutBatchService;
import com.sealease.backend.withdrawal.service.ReferralPayoutModeService;
import com.sealease.backend.withdrawal.service.WithdrawalRunService;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import com.sealease.backend.withdrawal.service.WithdrawalSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class AdminWithdrawalController {

	private static final MediaType XLSX =
			MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final WithdrawalService withdrawals;
	private final PayoutBatchService batches;
	private final WithdrawalRunService runs;
	private final ReferralPayoutModeService referralModes;
	private final WithdrawalSettingsService settings;
	private final UserAccountService accounts;
	private final WithdrawalReviewService reviews;
	private final WithdrawalPaymentService payments;
	private final DisplayTimeZone zone;

	public AdminWithdrawalController(WithdrawalService withdrawals, PayoutBatchService batches, WithdrawalRunService runs,
			ReferralPayoutModeService referralModes, WithdrawalSettingsService settings, UserAccountService accounts,
			WithdrawalReviewService reviews, WithdrawalPaymentService payments, DisplayTimeZone zone) {
		this.accounts = accounts;
		this.reviews = reviews;
		this.payments = payments;
		this.zone = zone;
		this.withdrawals = withdrawals;
		this.batches = batches;
		this.runs = runs;
		this.referralModes = referralModes;
		this.settings = settings;
	}

	// -------------------------------------------------------------------------- withdrawals

	/** The queue at a glance: counts and amounts per stage, paid today and this month. */
	@GetMapping("/api/v1/admin/withdrawals/dashboard")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public WithdrawalDashboard dashboard() {
		return reviews.dashboard();
	}

	/**
	 * Withdrawals, paged and sorted on the server. Filters: status, origin, investor, requested between {@code from}
	 * and {@code to} (dates, inclusive, in the display time zone), amount range, {@code mine} (being reviewed by me),
	 * and {@code q}: a whole reference, or an investor's readable ID or email.
	 */
	@GetMapping("/api/v1/admin/withdrawals")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public PageResponse<WithdrawalResponse> search(AuthenticatedUser actor,
			@RequestParam(required = false) WithdrawalStatus status,
			@RequestParam(required = false) String currency, @RequestParam(required = false) UUID userId,
			@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) BigDecimal minAmount, @RequestParam(required = false) BigDecimal maxAmount,
			@RequestParam(required = false) WithdrawalOrigin origin,
			@RequestParam(defaultValue = "false") boolean mine,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(withdrawals.search(new WithdrawalSearchCriteria(status, currency, userId, q,
				q == null || q.isBlank() ? null : accounts.findIdExactly(q).orElse(null),
				from == null ? null : from.atStartOfDay(zone.zone()).toInstant(),
				to == null ? null : to.plusDays(1).atStartOfDay(zone.zone()).toInstant(), minAmount, maxAmount, origin,
				mine ? actor.userId() : null), pageable));
	}

	/** One withdrawal with its investor, timeline (every step and who made it) and internal notes. */
	@GetMapping("/api/v1/admin/withdrawals/{withdrawalId}")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public WithdrawalAdminDetail detail(@PathVariable UUID withdrawalId) {
		return reviews.detail(withdrawalId);
	}

	/** Takes a submitted request for review. */
	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/review")
	@PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")
	public WithdrawalResponse take(AuthenticatedUser actor, @PathVariable UUID withdrawalId) {
		return reviews.take(actor.userId(), withdrawalId);
	}

	/** Gives it back to the queue. */
	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/review/release")
	@PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")
	public WithdrawalResponse releaseReview(AuthenticatedUser actor, @PathVariable UUID withdrawalId) {
		return reviews.release(actor.userId(), withdrawalId);
	}

	/** Approves (first or second approval). Never one's own; a second approval comes from someone else. */
	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/approve")
	@PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")
	public WithdrawalResponse approve(AuthenticatedUser actor, @PathVariable UUID withdrawalId) {
		return reviews.approve(actor.userId(), withdrawalId);
	}

	/** Rejects, with a reason the investor sees; the amount goes back to their wallet. */
	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/reject")
	@PreAuthorize("hasAuthority('WITHDRAWAL_REJECT')")
	public WithdrawalResponse reject(AuthenticatedUser actor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody WithdrawalDecisionRequest request) {
		return reviews.reject(actor.userId(), withdrawalId, request.reason());
	}

	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/hold")
	@PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")
	public WithdrawalResponse hold(AuthenticatedUser actor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody WithdrawalDecisionRequest request) {
		return reviews.hold(actor.userId(), withdrawalId, request.reason(), false);
	}

	/** On hold, asking the investor for more information; back to review when they answer. */
	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/request-info")
	@PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")
	public WithdrawalResponse requestInfo(AuthenticatedUser actor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody WithdrawalDecisionRequest request) {
		return reviews.hold(actor.userId(), withdrawalId, request.reason(), true);
	}

	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/resume")
	@PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")
	public WithdrawalResponse resume(AuthenticatedUser actor, @PathVariable UUID withdrawalId) {
		return reviews.resume(actor.userId(), withdrawalId);
	}

	/** An internal note: staff only, never shown to the investor. */
	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/notes")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	@ResponseStatus(HttpStatus.CREATED)
	public WithdrawalNoteResponse addNote(AuthenticatedUser actor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody WithdrawalDecisionRequest request) {
		return reviews.addNote(actor.userId(), withdrawalId, request.reason());
	}

	/** Hands approved requests to the payout provider. Payment completes only from the bank's evidence. */
	@PostMapping("/api/v1/admin/withdrawals/initiate-payment")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public InitiatePaymentResponse initiatePayment(AuthenticatedUser actor,
			@Valid @RequestBody InitiatePaymentRequest request) {
		return payments.initiate(actor.userId(), request.withdrawalIds());
	}

	/** A paid withdrawal's receipt (PDF), as the investor gets it. */
	@GetMapping("/api/v1/admin/withdrawals/{withdrawalId}/receipt")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public ResponseEntity<byte[]> receipt(@PathVariable UUID withdrawalId) {
		return WithdrawalController.pdf(payments.receipt(null, withdrawalId));
	}

	/** A failed bank payment, settled: paid by hand, or returned to the investor's balance. Resolves its ticket. */
	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/resolve")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public WithdrawalResponse resolve(AuthenticatedUser actor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody ResolveFailedRequest request) {
		return batches.resolveFailed(actor.userId(), withdrawalId, request.outcome() == ResolveFailedRequest.Outcome.PAID,
				request.note());
	}

	/** How an investor's referral earnings are paid this year. */
	@GetMapping("/api/v1/admin/users/{userId}/referral-mode")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public ReferralPayoutMode referralMode(@PathVariable UUID userId) {
		return referralModes.current(userId);
	}

	/** Changes it on the investor's request (they choose only once a year themselves). */
	@PutMapping("/api/v1/admin/users/{userId}/referral-mode")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public ReferralPayoutMode setReferralMode(AuthenticatedUser actor, @PathVariable UUID userId,
			@Valid @RequestBody ReferralPayoutModeRequest request) {
		return referralModes.set(actor.userId(), userId, request.mode());
	}

	// ------------------------------------------------------------------------------ batches

	@GetMapping("/api/v1/admin/withdrawal-batches")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public PageResponse<BatchResponse> batches(@RequestParam(required = false) BatchStatus status,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(batches.search(status, pageable));
	}

	@GetMapping("/api/v1/admin/withdrawal-batches/{batchId}")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public BatchDetail batch(@PathVariable UUID batchId) {
		return batches.detail(batchId);
	}

	/** The batch's Excel file with full account numbers; every download is audited. */
	@GetMapping("/api/v1/admin/withdrawal-batches/{batchId}/file")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public ResponseEntity<byte[]> file(AuthenticatedUser actor, @PathVariable UUID batchId) {
		return download(batches.file(actor.userId(), batchId));
	}

	/** The files kept with a batch: its payment file and the status files uploaded for it. */
	@GetMapping("/api/v1/admin/withdrawal-batches/{batchId}/files")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public List<BatchFileResponse> files(@PathVariable UUID batchId) {
		return batches.files(batchId);
	}

	/** One kept file, with full account numbers; every download is audited. */
	@GetMapping("/api/v1/admin/withdrawal-batches/{batchId}/files/{fileId}")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public ResponseEntity<byte[]> storedFile(AuthenticatedUser actor, @PathVariable UUID batchId, @PathVariable UUID fileId) {
		return download(batches.storedFile(actor.userId(), batchId, fileId));
	}

	// ------------------------------------------------------------------------------- runs

	@GetMapping("/api/v1/admin/withdrawal-runs")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public List<WithdrawalRunResponse> runs() {
		return runs.recent(24);
	}

	/**
	 * Generates rent ({@code kind=RENT}) or referral ({@code kind=REFERRAL}) withdrawals: every eligible investor's
	 * balances of that kind are withdrawn (in progress) into a bank sheet and a cash sheet for finance to pay.
	 */
	@PostMapping("/api/v1/admin/withdrawal-runs")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public WithdrawalRunResponse generate(AuthenticatedUser actor, @RequestParam WithdrawalKind kind) {
		return runs.generate(actor.userId(), kind);
	}

	// ----------------------------------------------------------------------------- settings

	@GetMapping("/api/v1/admin/settings/withdrawals")
	@PreAuthorize("hasAnyAuthority('SYSTEM_SETTINGS_MANAGE', 'WITHDRAWAL_VIEW')")
	public WithdrawalSettingsResponse settings() {
		return settings.get();
	}

	/** Applies from the next withdrawal run. Set by whoever generates the runs (admins), or system settings. */
	@PutMapping("/api/v1/admin/settings/withdrawals")
	@PreAuthorize("hasAnyAuthority('SYSTEM_SETTINGS_MANAGE', 'WITHDRAWAL_PROCESS')")
	public WithdrawalSettingsResponse updateSettings(AuthenticatedUser admin,
			@Valid @RequestBody WithdrawalSettingsRequest request) {
		return settings.update(admin.userId(), request);
	}

	private static ResponseEntity<byte[]> download(PayoutBatchService.PayoutFile file) {
		return ResponseEntity.ok()
			.contentType(file.excel() ? XLSX : new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
			.cacheControl(CacheControl.noStore())
			.body(file.content());
	}

}
