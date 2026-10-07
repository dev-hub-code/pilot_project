package com.sealease.backend.referral.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.time.Instant;
import java.util.List;

/**
 * One member of the investor's downline. Ids are opaque to this response (not account ids): they
 * only link members to their referrer within the tree.
 *
 * @param parentId {@code null} for direct referrals (level 1)
 * @param earned   commission the viewer has earned from this member, per currency
 */
public record DownlineMember(String id, String parentId, int level, String displayName, Instant joinedAt,
		List<MoneyResponse> earned) {
}
