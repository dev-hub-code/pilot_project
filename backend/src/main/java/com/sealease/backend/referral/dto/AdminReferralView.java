package com.sealease.backend.referral.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;
import java.util.UUID;

/**
 * A user's place in the referral hierarchy, for staff.
 *
 * @param code         {@code null} until the user has opened their referral page
 * @param signupsJoinNetwork whether people signing up with their code become network investors too
 * @param downlineSize members per level, levels 1-4
 */
public record AdminReferralView(String code, ReferralScheme scheme, boolean signupsJoinNetwork, List<Ancestor> uplines, List<Long> downlineSize,
		List<MoneyResponse> totalEarned) {

	public record Ancestor(int level, UUID userId, String name, String email) {
	}

}
