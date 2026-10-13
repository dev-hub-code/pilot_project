package com.sealease.backend.kyc.entity;

/** DRAFT while the investor has uploaded only one of Aadhaar and PAN; PENDING (under review) once both are in. */
public enum KycSubmissionStatus {
	DRAFT,
	PENDING,
	APPROVED,
	REJECTED
}
