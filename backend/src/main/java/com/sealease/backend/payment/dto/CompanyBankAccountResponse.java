package com.sealease.backend.payment.dto;

import com.sealease.backend.payment.entity.CompanyBankAccount;

import java.time.Instant;
import java.util.UUID;

public record CompanyBankAccountResponse(
		UUID id,
		String accountName,
		String bankName,
		String branch,
		String accountNumber,
		String ifscCode,
		String upiId,
		boolean active,
		Instant createdAt) {

	public static CompanyBankAccountResponse from(CompanyBankAccount a) {
		return new CompanyBankAccountResponse(a.getId(), a.getAccountName(), a.getBankName(), a.getBranch(),
				a.getAccountNumber(), a.getIfscCode(), a.getUpiId(), a.isActive(), a.getCreatedAt());
	}

}
