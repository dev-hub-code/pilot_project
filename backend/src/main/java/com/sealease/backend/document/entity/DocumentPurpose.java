package com.sealease.backend.document.entity;

public enum DocumentPurpose {
	KYC_IDENTITY_FRONT,
	KYC_IDENTITY_BACK,
	KYC_SELFIE,
	KYC_PROOF_OF_ADDRESS,
	KYC_AADHAAR_CARD,
	KYC_PAN_CARD,
	/** The investor's signature, signed on white paper and photographed or scanned. */
	KYC_SIGNATURE,
	/** Front and back as separate uploads, on submissions made before a single Aadhaar file. */
	KYC_AADHAAR_FRONT,
	KYC_AADHAAR_BACK,

	LEASE_AGREEMENT,
	INSURANCE_CERTIFICATE,
	OFFERING_MEMORANDUM,
	BANK_STATEMENT,
	CANCELLED_CHEQUE,
	BANK_PASSBOOK,

	SUPPORT_ATTACHMENT
}
