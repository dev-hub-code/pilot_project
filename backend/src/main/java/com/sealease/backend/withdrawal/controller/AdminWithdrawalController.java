package com.sealease.backend.withdrawal.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.api.ReasonRequest;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.withdrawal.dto.BatchDetail;
import com.sealease.backend.withdrawal.dto.BatchFileResponse;
import com.sealease.backend.withdrawal.dto.BatchResponse;
import com.sealease.backend.withdrawal.dto.CashHandOverRequest;
import com.sealease.backend.withdrawal.dto.MarkPaidRequest;
import com.sealease.backend.withdrawal.dto.OfficeWithdrawalOption;
import com.sealease.backend.withdrawal.dto.OfficeWithdrawalRequest;
import com.sealease.backend.withdrawal.dto.ReconciliationResult;
import com.sealease.backend.withdrawal.dto.ReferralPayoutMode;
import com.sealease.backend.withdrawal.dto.ReferralPayoutModeRequest;
import com.sealease.backend.withdrawal.dto.ResolveFailedRequest;
import com.sealease.backend.withdrawal.dto.ScheduledRunResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalSearchCriteria;
import com.sealease.backend.withdrawal.dto.WithdrawalSettingsRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalSettingsResponse;
import com.sealease.backend.withdrawal.entity.BatchStatus;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.service.PayoutBatchService;
import com.sealease.backend.withdrawal.service.ReferralPayoutModeService;
import com.sealease.backend.withdrawal.service.ScheduledWithdrawalService;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class AdminWithdrawalController {

	/** Largest status file accepted (a month's file is far smaller). */
	private static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
	private static final MediaType XLSX =
			MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final WithdrawalService withdrawals;
	private final PayoutBatchService batches;
	private final ScheduledWithdrawalService runs;
	private final WithdrawalSettingsService settings;
	private final ReferralPayoutModeService referralModes;

	public AdminWithdrawalController(WithdrawalService withdrawals, PayoutBatchService batches,
			ScheduledWithdrawalService runs, WithdrawalSettingsService settings, ReferralPayoutModeService referralModes) {
		this.withdrawals = withdrawals;
		this.batches = batches;
		this.runs = runs;
		this.settings = settings;
		this.referralModes = referralModes;
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

	/** What can be withdrawn in cash at the office for an investor today. */
	@GetMapping("/api/v1/admin/withdrawals/office")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public List<OfficeWithdrawalOption> officeOptions(@RequestParam UUID userId) {
		return withdrawals.officeOptions(userId);
	}

	/** Cash withdrawn at the office in front of the investor: their whole balance, ready to hand over. */
	@PostMapping("/api/v1/admin/withdrawals/office")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public WithdrawalResponse atOffice(AuthenticatedUser actor, @Valid @RequestBody OfficeWithdrawalRequest request) {
		return withdrawals.atOffice(actor.userId(), request.userId(), request.balance());
	}

	/** Cash handed over at the office: finance records it with the receipt number. */
	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/hand-over")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public WithdrawalResponse handOver(AuthenticatedUser actor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody CashHandOverRequest request) {
		return withdrawals.handOverCash(actor.userId(), withdrawalId, request.receiptNumber());
	}

	/** A cash withdrawal not handed over is cancelled; the money returns to the balance. */
	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/reject")
	@PreAuthorize("hasAuthority('WITHDRAWAL_REJECT')")
	public WithdrawalResponse reject(AuthenticatedUser actor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody ReasonRequest request) {
		return withdrawals.reject(actor.userId(), withdrawalId, request.reason());
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

	/** The batch's Excel file uploaded back after paying it, with Status (PAID, FAILED or blank) and Remarks per row. */
	@PostMapping(path = "/api/v1/admin/withdrawal-batches/{batchId}/reconciliation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public ReconciliationResult reconcile(AuthenticatedUser actor, @PathVariable UUID batchId,
			@RequestPart("file") MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose the Excel file to upload");
		}
		if (file.getSize() > MAX_UPLOAD_BYTES) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The file is larger than 5 MB");
		}
		return batches.reconcile(actor.userId(), batchId, file.getOriginalFilename(), file.getBytes());
	}

	@PostMapping("/api/v1/admin/withdrawal-batches/{batchId}/sent")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public BatchDetail sent(AuthenticatedUser actor, @PathVariable UUID batchId) {
		return batches.markSent(actor.userId(), batchId);
	}

	@PostMapping("/api/v1/admin/withdrawal-batches/{batchId}/items/{withdrawalId}/paid")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public BatchDetail paid(AuthenticatedUser actor, @PathVariable UUID batchId, @PathVariable UUID withdrawalId,
			@Valid @RequestBody MarkPaidRequest request) {
		return batches.markPaid(actor.userId(), batchId, withdrawalId, request.payoutReference());
	}

	@PostMapping("/api/v1/admin/withdrawal-batches/{batchId}/items/{withdrawalId}/failed")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public BatchDetail failed(AuthenticatedUser actor, @PathVariable UUID batchId, @PathVariable UUID withdrawalId,
			@Valid @RequestBody ReasonRequest request) {
		return batches.markFailed(actor.userId(), batchId, withdrawalId, request.reason());
	}

	@PostMapping("/api/v1/admin/withdrawal-batches/{batchId}/settle")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public BatchDetail settle(AuthenticatedUser actor, @PathVariable UUID batchId,
			@Valid @RequestBody MarkPaidRequest request) {
		return batches.markRemainingPaid(actor.userId(), batchId, request.payoutReference());
	}

	// --------------------------------------------------------------------- scheduled runs

	@GetMapping("/api/v1/admin/settings/withdrawals")
	@PreAuthorize("hasAnyAuthority('SYSTEM_SETTINGS_MANAGE', 'WITHDRAWAL_VIEW')")
	public WithdrawalSettingsResponse withdrawalSettings() {
		return settings.get();
	}

	@PutMapping("/api/v1/admin/settings/withdrawals")
	@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_MANAGE')")
	public WithdrawalSettingsResponse updateWithdrawalSettings(AuthenticatedUser admin,
			@Valid @RequestBody WithdrawalSettingsRequest request) {
		return settings.update(admin.userId(), request.rentWithdrawalDay(), request.referralWithdrawalDay());
	}

	@GetMapping("/api/v1/admin/withdrawal-runs")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public List<ScheduledRunResponse> runs() {
		return runs.recent(24);
	}

	/** Makes this month's rent or referral withdrawals now, without waiting for the file day. */
	@PostMapping("/api/v1/admin/withdrawal-runs")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public ScheduledRunResponse runNow(AuthenticatedUser actor, @RequestParam(defaultValue = "RENT") WithdrawalKind kind) {
		return runs.runNow(actor.userId(), kind);
	}

	private static ResponseEntity<byte[]> download(PayoutBatchService.PayoutFile file) {
		return ResponseEntity.ok()
			.contentType(file.excel() ? XLSX : new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
			.cacheControl(CacheControl.noStore())
			.body(file.content());
	}

}
