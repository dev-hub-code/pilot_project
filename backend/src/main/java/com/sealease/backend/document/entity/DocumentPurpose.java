package com.sealease.backend.document.entity;

public enum DocumentPurpose {
	KYC_IDENTITY_FRONT,
	KYC_IDENTITY_BACK,
	KYC_SELFIE,
	KYC_PROOF_OF_ADDRESS,
	KYC_AADHAAR_CARD,
	KYC_PAN_CARD,
	/** Front and back as separate uploads, on submissions made before a single Aadhaar file. */
	KYC_AADHAAR_FRONT,
	KYC_AADHAAR_BACK,

	CONTAINER_PHOTO,
	CONTAINER_SURVEY_REPORT,
	LEASE_AGREEMENT,
	INSURANCE_CERTIFICATE,
	OFFERING_MEMORANDUM,
	PLAN_PHOTO,
	BANK_STATEMENT,
	CANCELLED_CHEQUE,

	SUPPORT_ATTACHMENT;

	public boolean isContainerDocument() {
		return name().startsWith("CONTAINER_") || this == LEASE_AGREEMENT || this == INSURANCE_CERTIFICATE
				|| this == OFFERING_MEMORANDUM;
	}

	public boolean isImage() {
		return this == CONTAINER_PHOTO;
	}
}
