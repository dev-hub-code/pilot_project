package com.sealease.backend.payment.dto;

import com.sealease.backend.payment.entity.DepositMode;

import java.time.Instant;
import java.util.UUID;

/** What the investor told us about a bank payment, for finance to check against the statement. */
public record DepositDetails(
		DepositMode mode,
		String reference,
		Instant submittedAt,
		UUID companyBankAccountId,
		String bankName,
		String accountNumber) {
}
