package com.sealease.backend.bankaccount.controller;

import com.sealease.backend.bankaccount.dto.AddBankAccountRequest;
import com.sealease.backend.bankaccount.dto.BankAccountResponse;
import com.sealease.backend.bankaccount.entity.BankProofType;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

	/**
	 * Multipart: a JSON part {@code account}, the {@code proofType} (BANK_STATEMENT or CANCELLED_CHEQUE) and the
	 * {@code proof} file.
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public BankAccountResponse add(AuthenticatedUser user, @Valid @RequestPart("account") AddBankAccountRequest request,
			@RequestParam(required = false) BankProofType proofType,
			@RequestPart(value = "proof", required = false) MultipartFile proof) {
		return bankAccounts.add(user.userId(), request, proofType, proof);
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
