package com.sealease.backend.payment.dto;

import com.sealease.backend.common.money.MoneyResponse;

/** @param reference must be quoted on the transfer so it can be matched to the payment */
public record BankTransferInstructions(String beneficiaryName, String iban, String bic, String bankName,
		String reference, MoneyResponse amount) {
}
