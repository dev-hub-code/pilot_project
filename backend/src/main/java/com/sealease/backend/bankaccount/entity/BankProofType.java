package com.sealease.backend.bankaccount.entity;

import com.sealease.backend.document.entity.DocumentPurpose;

/** What the investor uploaded to prove the bank account is theirs. */
public enum BankProofType {
	BANK_STATEMENT(DocumentPurpose.BANK_STATEMENT),
	CANCELLED_CHEQUE(DocumentPurpose.CANCELLED_CHEQUE);

	private final DocumentPurpose purpose;

	BankProofType(DocumentPurpose purpose) {
		this.purpose = purpose;
	}

	public DocumentPurpose purpose() {
		return purpose;
	}
}
