package com.sealease.backend.bankaccount.dto;

import com.sealease.backend.bankaccount.entity.BankAccount;
import com.sealease.backend.bankaccount.entity.BankAccountStatus;
import com.sealease.backend.bankaccount.entity.BankProofType;

import java.time.Instant;
import java.util.UUID;

/**
 * Masked bank account; the full number and routing code are never returned by the API.
 *
 * @param replacesAccountId set while (or after) this account was a requested change of another account
 * @param proofType         the bank statement or cancelled cheque uploaded with it; null for accounts added before
 */
public record BankAccountResponse(
		UUID id,
		String accountHolderName,
		String bankName,
		String country,
		String currency,
		String accountNumberMasked,
		BankAccountStatus status,
		boolean primary,
		String rejectionReason,
		Instant createdAt,
		Instant verifiedAt,
		UUID replacesAccountId,
		BankProofType proofType) {

	public static BankAccountResponse from(BankAccount account) {
		return new BankAccountResponse(account.getId(), account.getAccountHolderName(), account.getBankName(),
				account.getCountry(), account.getCurrency(), "•••• " + account.getAccountNumberLast4(),
				account.getStatus(), account.isPrimary(), account.getRejectionReason(), account.getCreatedAt(),
				account.getVerifiedAt(), account.getReplacesAccountId(), account.getProofType());
	}

}
