package com.sealease.backend.withdrawal.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.withdrawal.dto.BatchDetail;
import com.sealease.backend.withdrawal.dto.BatchFileResponse;
import com.sealease.backend.withdrawal.dto.BatchResponse;
import com.sealease.backend.withdrawal.dto.ReferralPayoutMode;
import com.sealease.backend.withdrawal.dto.ReferralPayoutModeRequest;
import com.sealease.backend.withdrawal.dto.ResolveFailedRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
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

	public AdminWithdrawalController(WithdrawalService withdrawals, PayoutBatchService batches, WithdrawalRunService runs,
			ReferralPayoutModeService referralModes, WithdrawalSettingsService settings) {
		this.withdrawals = withdrawals;
		this.batches = batches;
		this.runs = runs;
		this.referralModes = referralModes;
		this.settings = settings;
	}

	// -------------------------------------------------------------------------- withdrawals

	@GetMapping("/api/v1/admin/withdrawals")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public PageResponse<WithdrawalResponse> search(@RequestParam(required = false) WithdrawalStatus status,
			@RequestParam(required = false) String currency, @RequestParam(required = false) UUID userId,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(withdrawals.search(new WithdrawalSearchCriteria(status, currency, userId), pageable));
	}

	@GetMapping("/api/v1/admin/withdrawals/{withdrawalId}")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public WithdrawalResponse detail(@PathVariable UUID withdrawalId) {
		return withdrawals.detail(withdrawalId);
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

	/** Applies from the next withdrawal run. */
	@PutMapping("/api/v1/admin/settings/withdrawals")
	@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_MANAGE')")
	public WithdrawalSettingsResponse updateSettings(AuthenticatedUser admin,
			@Valid @RequestBody WithdrawalSettingsRequest request) {
		return settings.update(admin.userId(), request.minimumAmount());
	}

	private static ResponseEntity<byte[]> download(PayoutBatchService.PayoutFile file) {
		return ResponseEntity.ok()
			.contentType(file.excel() ? XLSX : new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
			.cacheControl(CacheControl.noStore())
			.body(file.content());
	}

}
