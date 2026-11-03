package com.sealease.backend.payment.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.time.Instant;

/**
 * Where to pay a pending virtual-account payment: an account number and IFSC for NEFT, RTGS or IMPS, or a UPI ID.
 * The order is confirmed automatically when the exact amount lands.
 */
public record VirtualAccountInstructions(MoneyResponse amount, String accountNumber, String ifsc, String accountName,
		String bankName, String upiId, Instant payBy) {
}
