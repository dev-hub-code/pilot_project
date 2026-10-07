package com.sealease.backend.bankaccount.controller;

import com.sealease.backend.bankaccount.dto.AdminBankAccountResponse;
import com.sealease.backend.bankaccount.entity.BankAccountStatus;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.api.ReasonRequest;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AdminBankAccountController {

	private final BankAccountService bankAccounts;

	public AdminBankAccountController(BankAccountService bankAccounts) {
		this.bankAccounts = bankAccounts;
	}

	@GetMapping("/api/v1/admin/bank-accounts")
	@PreAuthorize("hasAuthority('BANK_ACCOUNT_VERIFY')")
	public PageResponse<AdminBankAccountResponse> queue(
			@RequestParam(defaultValue = "PENDING_VERIFICATION") BankAccountStatus status,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
		return PageResponse.from(bankAccounts.queue(status, pageable));
	}

	/** Bank details are need-to-know: verifiers and finance only, not every USER_VIEW holder. */
	@GetMapping("/api/v1/admin/users/{userId}/bank-accounts")
	@PreAuthorize("hasAnyAuthority('BANK_ACCOUNT_VERIFY', 'FINANCE_VIEW')")
	public List<AdminBankAccountResponse> forUser(@PathVariable UUID userId) {
		return bankAccounts.adminListFor(userId);
	}

	@PostMapping("/api/v1/admin/bank-accounts/{accountId}/verify")
	@PreAuthorize("hasAuthority('BANK_ACCOUNT_VERIFY')")
	public AdminBankAccountResponse verify(AuthenticatedUser reviewer, @PathVariable UUID accountId) {
		return bankAccounts.verify(reviewer.userId(), accountId);
	}

	@PostMapping("/api/v1/admin/bank-accounts/{accountId}/reject")
	@PreAuthorize("hasAuthority('BANK_ACCOUNT_VERIFY')")
	public AdminBankAccountResponse reject(AuthenticatedUser reviewer, @PathVariable UUID accountId,
			@Valid @RequestBody ReasonRequest request) {
		return bankAccounts.reject(reviewer.userId(), accountId, request.reason());
	}

}
