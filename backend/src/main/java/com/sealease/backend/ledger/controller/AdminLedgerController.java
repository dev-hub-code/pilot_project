package com.sealease.backend.ledger.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.ledger.dto.AdjustmentRequest;
import com.sealease.backend.ledger.dto.LedgerAccountResponse;
import com.sealease.backend.ledger.dto.LedgerEntryResponse;
import com.sealease.backend.ledger.dto.TrialBalanceLine;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/ledger")
public class AdminLedgerController {

	private final LedgerService ledger;

	public AdminLedgerController(LedgerService ledger) {
		this.ledger = ledger;
	}

	@GetMapping("/accounts")
	@PreAuthorize("hasAuthority('FINANCE_VIEW')")
	public PageResponse<LedgerAccountResponse> accounts(@RequestParam(required = false) AccountType type,
			@RequestParam(required = false) UUID userId, @PageableDefault(size = 50) Pageable pageable) {
		return PageResponse.from(ledger.accounts(type, userId, pageable));
	}

	@GetMapping("/accounts/{accountId}")
	@PreAuthorize("hasAuthority('FINANCE_VIEW')")
	public LedgerAccountResponse account(@PathVariable UUID accountId) {
		return ledger.account(accountId);
	}

	@GetMapping("/accounts/{accountId}/entries")
	@PreAuthorize("hasAuthority('FINANCE_VIEW')")
	public PageResponse<LedgerEntryResponse> entries(@PathVariable UUID accountId,
			@PageableDefault(size = 50) Pageable pageable) {
		return PageResponse.from(ledger.entries(accountId, pageable));
	}

	@GetMapping("/trial-balance")
	@PreAuthorize("hasAuthority('FINANCE_VIEW')")
	public List<TrialBalanceLine> trialBalance() {
		return ledger.trialBalance();
	}

	@PostMapping("/adjustments")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('FINANCE_ADJUST')")
	public LedgerAccountResponse adjust(AuthenticatedUser actor,
			@RequestHeader(IdempotencyKey.HEADER) String idempotencyKey,
			@Valid @RequestBody AdjustmentRequest request) {
		return ledger.adjust(actor.userId(), request, idempotencyKey);
	}

}
