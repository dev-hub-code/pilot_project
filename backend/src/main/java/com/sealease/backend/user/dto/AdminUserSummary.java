package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserProfile;
import com.sealease.backend.user.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserSummary(
		UUID id,
		/** The readable ID ("SL03500"). */
		String userCode,
		String email,
		String firstName,
		String lastName,
		UserStatus status,
		KycStatus kycStatus,
		/** When the user confirmed their email address; null while it is unverified. */
		Instant emailVerifiedAt,
		Instant createdAt,
		Instant lastLoginAt,
		/** Investor or employee. */
		UserKind kind) {

	public static AdminUserSummary from(UserProfile p, UserKind kind) {
		var u = p.getUser();
		return new AdminUserSummary(u.getId(), u.getUserCode(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getStatus(),
				p.getKycStatus(), u.getEmailVerifiedAt(), u.getCreatedAt(), u.getLastLoginAt(), kind);
	}

}
