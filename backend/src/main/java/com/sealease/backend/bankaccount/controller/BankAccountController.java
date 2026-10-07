package com.sealease.backend.bankaccount.controller;

import com.sealease.backend.bankaccount.dto.AddBankAccountRequest;
import com.sealease.backend.bankaccount.dto.BankAccountResponse;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Payout accounts belong to the investor portal. */
@RestController
@RequestMapping("/api/v1/users/me/bank-accounts")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class BankAccountController {

	private final BankAccountService bankAccounts;

	public BankAccountController(BankAccountService bankAccounts) {
		this.bankAccounts = bankAccounts;
	}

	@GetMapping
	public List<BankAccountResponse> list(AuthenticatedUser user) {
		return bankAccounts.listFor(user.userId());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BankAccountResponse add(AuthenticatedUser user, @Valid @RequestBody AddBankAccountRequest request) {
		return bankAccounts.add(user.userId(), request);
	}

	@DeleteMapping("/{accountId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(AuthenticatedUser user, @PathVariable UUID accountId) {
		bankAccounts.remove(user.userId(), accountId);
	}

	@PutMapping("/{accountId}/primary")
	public List<BankAccountResponse> makePrimary(AuthenticatedUser user, @PathVariable UUID accountId) {
		return bankAccounts.makePrimary(user.userId(), accountId);
	}

}
