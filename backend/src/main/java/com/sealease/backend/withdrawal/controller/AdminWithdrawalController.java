package com.sealease.backend.withdrawal.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.api.ReasonRequest;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.withdrawal.dto.BatchDetail;
import com.sealease.backend.withdrawal.dto.BatchResponse;
import com.sealease.backend.withdrawal.dto.CreateBatchRequest;
import com.sealease.backend.withdrawal.dto.MarkPaidRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalSearchCriteria;
import com.sealease.backend.withdrawal.entity.BatchStatus;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.service.PayoutBatchService;
import com.sealease.backend.withdrawal.service.WithdrawalService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class AdminWithdrawalController {

	private final WithdrawalService withdrawals;
	private final PayoutBatchService batches;

	public AdminWithdrawalController(WithdrawalService withdrawals, PayoutBatchService batches) {
		this.withdrawals = withdrawals;
		this.batches = batches;
	}

	// -------------------------------------------------------------------------- withdrawals

	@GetMapping("/api/v1/admin/withdrawals")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public PageResponse<WithdrawalResponse> search(@RequestParam(required = false) WithdrawalStatus status,
			@RequestParam(required = false) String currency, @RequestParam(required = false) UUID userId,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
		return PageResponse.from(withdrawals.search(new WithdrawalSearchCriteria(status, currency, userId), pageable));
	}

	@GetMapping("/api/v1/admin/withdrawals/{withdrawalId}")
	@PreAuthorize("hasAuthority('WITHDRAWAL_VIEW')")
	public WithdrawalResponse detail(@PathVariable UUID withdrawalId) {
		return withdrawals.detail(withdrawalId);
	}

	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/approve")
	@PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")
	public WithdrawalResponse approve(AuthenticatedUser actor, @PathVariable UUID withdrawalId) {
		return withdrawals.approve(actor.userId(), withdrawalId);
	}

	@PostMapping("/api/v1/admin/withdrawals/{withdrawalId}/reject")
	@PreAuthorize("hasAuthority('WITHDRAWAL_REJECT')")
	public WithdrawalResponse reject(AuthenticatedUser actor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody ReasonRequest request) {
		return withdrawals.reject(actor.userId(), withdrawalId, request.reason());
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

	@PostMapping("/api/v1/admin/withdrawal-batches")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public BatchDetail create(AuthenticatedUser actor, @Valid @RequestBody CreateBatchRequest request) {
		return batches.create(actor.userId(), request.currency());
	}

	/** Bank payment file with full account numbers; every download is audited. */
	@GetMapping("/api/v1/admin/withdrawal-batches/{batchId}/file")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public ResponseEntity<byte[]> file(AuthenticatedUser actor, @PathVariable UUID batchId) {
		PayoutBatchService.PayoutFile file = batches.file(actor.userId(), batchId);
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
			.cacheControl(CacheControl.noStore())
			.body(file.content().getBytes(StandardCharsets.UTF_8));
	}

	@PostMapping("/api/v1/admin/withdrawal-batches/{batchId}/cancel")
	@PreAuthorize("hasAuthority('WITHDRAWAL_PROCESS')")
	public BatchDetail cancel(AuthenticatedUser actor, @PathVariable UUID batchId) {
		return batches.cancel(actor.userId(), batchId);
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

}
