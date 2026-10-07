package com.sealease.backend.bankaccount.dto;

import com.sealease.backend.bankaccount.entity.BankAccountStatus;

import java.util.UUID;

/** A bank account as the withdrawal module sees it: whose it is, whether it may be paid, how to show it. */
public record PayoutAccount(UUID id, UUID userId, String holderName, String bankName, String country,
		String currency, String last4, BankAccountStatus status) {

	public boolean isVerified() {
		return status == BankAccountStatus.VERIFIED;
	}

}
