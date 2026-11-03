package com.sealease.backend.bankaccount.dto;

import com.sealease.backend.bankaccount.entity.BankAccount;
import com.sealease.backend.bankaccount.entity.BankAccountStatus;
import com.sealease.backend.bankaccount.entity.BankProofType;

import java.time.Instant;
import java.util.UUID;

/**
 * A bank account, shown to its owner and to staff with its full account number, unmasked.
 *
 * @param accountNumber       the full account number
 * @param ifsc                the branch's IFSC
 * @param accountNumberMasked the last four digits only, e.g. for notifications
 * @param replacesAccountId set while (or after) this account was a requested change of another account
 * @param proofType         the bank statement, cancelled cheque or passbook uploaded with it; null for accounts added before
 */
public record BankAccountResponse(
		UUID id,
		String accountHolderName,
		String bankName,
		String country,
		String currency,
		String accountNumber,
		String ifsc,
		String accountNumberMasked,
		BankAccountStatus status,
		boolean primary,
		String rejectionReason,
		Instant createdAt,
		Instant verifiedAt,
		UUID replacesAccountId,
		BankProofType proofType) {

	/** @param accountNumber the account's full number, decrypted; {@code ifsc} likewise */
	public static BankAccountResponse from(BankAccount account, String accountNumber, String ifsc) {
		return new BankAccountResponse(account.getId(), account.getAccountHolderName(), account.getBankName(),
				account.getCountry(), account.getCurrency(), accountNumber, ifsc, "•••• " + account.getAccountNumberLast4(),
				account.getStatus(), account.isPrimary(), account.getRejectionReason(), account.getCreatedAt(),
				account.getVerifiedAt(), account.getReplacesAccountId(), account.getProofType());
	}

}
