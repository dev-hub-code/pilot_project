package com.sealease.backend.payment.controller;

import com.sealease.backend.payment.dto.CompanyBankAccountRequest;
import com.sealease.backend.payment.dto.CompanyBankAccountResponse;
import com.sealease.backend.payment.service.CompanyBankAccountService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The company bank accounts investors pay into. */
@RestController
public class AdminCompanyBankAccountController {

	private final CompanyBankAccountService accounts;

	public AdminCompanyBankAccountController(CompanyBankAccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping("/api/v1/admin/company-bank-accounts")
	@PreAuthorize("hasAnyAuthority('COMPANY_BANK_ACCOUNT_MANAGE', 'FINANCE_VIEW')")
	public List<CompanyBankAccountResponse> list() {
		return accounts.list();
	}

	@PostMapping("/api/v1/admin/company-bank-accounts")
	@PreAuthorize("hasAuthority('COMPANY_BANK_ACCOUNT_MANAGE')")
	@ResponseStatus(HttpStatus.CREATED)
	public CompanyBankAccountResponse create(AuthenticatedUser actor, @Valid @RequestBody CompanyBankAccountRequest request) {
		return accounts.create(actor.userId(), request);
	}

	@PutMapping("/api/v1/admin/company-bank-accounts/{accountId}")
	@PreAuthorize("hasAuthority('COMPANY_BANK_ACCOUNT_MANAGE')")
	public CompanyBankAccountResponse update(AuthenticatedUser actor, @PathVariable UUID accountId,
			@Valid @RequestBody CompanyBankAccountRequest request) {
		return accounts.update(actor.userId(), accountId, request);
	}

	@PostMapping("/api/v1/admin/company-bank-accounts/{accountId}/activate")
	@PreAuthorize("hasAuthority('COMPANY_BANK_ACCOUNT_MANAGE')")
	public CompanyBankAccountResponse activate(AuthenticatedUser actor, @PathVariable UUID accountId) {
		return accounts.setActive(actor.userId(), accountId, true);
	}

	@PostMapping("/api/v1/admin/company-bank-accounts/{accountId}/deactivate")
	@PreAuthorize("hasAuthority('COMPANY_BANK_ACCOUNT_MANAGE')")
	public CompanyBankAccountResponse deactivate(AuthenticatedUser actor, @PathVariable UUID accountId) {
		return accounts.setActive(actor.userId(), accountId, false);
	}

}
