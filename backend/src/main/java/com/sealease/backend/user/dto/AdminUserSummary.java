package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserProfile;
import com.sealease.backend.user.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserSummary(
		UUID id,
		String email,
		String firstName,
		String lastName,
		UserStatus status,
		KycStatus kycStatus,
		Instant createdAt,
		Instant lastLoginAt) {

	public static AdminUserSummary from(UserProfile p) {
		var u = p.getUser();
		return new AdminUserSummary(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getStatus(),
				p.getKycStatus(), u.getCreatedAt(), u.getLastLoginAt());
	}

}
