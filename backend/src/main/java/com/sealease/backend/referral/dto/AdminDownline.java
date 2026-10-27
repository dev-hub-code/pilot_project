package com.sealease.backend.referral.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An investor's whole downline, for staff: every level (not only the four that earn commission), with account ids so
 * super administrators can move members.
 *
 * @param truncated the downline is larger than the response limit; only the earliest members are listed
 */
public record AdminDownline(Investor investor, List<Member> members, boolean truncated) {

	/**
	 * The investor whose downline it is; they can be moved too.
	 *
	 * @param referrerId   their own referrer, {@code null} when nobody referred them
	 * @param referrerName that referrer's name, {@code null} when nobody referred them
	 */
	public record Investor(UUID userId, String name, String email, String status, UUID referrerId, String referrerName) {
	}

	/**
	 * @param referrerId the member's referrer: the investor whose downline this is, or another member
	 * @param level      1 for the investor's direct referrals
	 * @param status     the account status (ACTIVE, SUSPENDED, DISABLED)
	 */
	public record Member(UUID userId, UUID referrerId, int level, String name, String email, String status,
			Instant joinedAt) {
	}

}
