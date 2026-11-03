package com.sealease.backend.kyc.entity;

/** DRAFT until the investor has uploaded the Aadhaar, the PAN and the signature; PENDING (under review) once all are in. */
public enum KycSubmissionStatus {
	DRAFT,
	PENDING,
	APPROVED,
	REJECTED
}
