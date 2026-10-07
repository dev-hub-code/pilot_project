package com.sealease.backend.user.entity;

/** Summary of the user's latest KYC submission, kept on the profile for fast filtering. */
public enum KycStatus {
	NOT_SUBMITTED,
	PENDING,
	APPROVED,
	REJECTED
}
