package com.sealease.backend.bankaccount.dto;

import java.util.UUID;

/**
 * Full, decrypted bank details for a payout file. Never logged: {@link #toString()} masks the numbers.
 *
 * @param routingCode BIC/SWIFT, sort code or routing number, as the investor entered it
 */
public record PayoutInstruction(UUID accountId, String holderName, String bankName, String country, String currency,
		String accountNumber, String routingCode) {

	@Override
	public String toString() {
		return "PayoutInstruction[accountId=" + accountId + ", accountNumber=****]";
	}

}
