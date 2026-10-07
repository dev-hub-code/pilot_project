package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.InvestorClassification;
import com.sealease.backend.user.entity.InvestorType;
import com.sealease.backend.user.entity.UserProfile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Staff view of an account. Bank and KYC details are deliberately absent: they are served by their
 * own endpoints behind dedicated permissions.
 */
public record AdminUserDetail(
		AdminUserSummary summary,
		ProfileResponse profile,
		String statusReason,
		Instant statusChangedAt,
		List<Classification> classificationHistory) {

	public record Classification(InvestorType previousType, InvestorType newType, String reason, UUID decidedBy,
			Instant decidedAt) {

		public static Classification from(InvestorClassification c) {
			return new Classification(c.getPreviousType(), c.getNewType(), c.getReason(), c.getDecidedBy(),
					c.getDecidedAt());
		}

	}

	public static AdminUserDetail from(UserProfile p, List<InvestorClassification> history) {
		return new AdminUserDetail(AdminUserSummary.from(p), ProfileResponse.from(p), p.getUser().getStatusReason(),
				p.getUser().getStatusChangedAt(), history.stream().map(Classification::from).toList());
	}

}
