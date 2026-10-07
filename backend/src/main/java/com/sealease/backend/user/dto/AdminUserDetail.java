package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.UserProfile;

import java.time.Instant;

/**
 * Staff view of an account. Bank and KYC details are deliberately absent: they are served by their
 * own endpoints behind dedicated permissions.
 */
public record AdminUserDetail(
		AdminUserSummary summary,
		ProfileResponse profile,
		String statusReason,
		Instant statusChangedAt) {

	public static AdminUserDetail from(UserProfile p) {
		return new AdminUserDetail(AdminUserSummary.from(p), ProfileResponse.from(p), p.getUser().getStatusReason(),
				p.getUser().getStatusChangedAt());
	}

}
