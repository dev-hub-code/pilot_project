package com.sealease.backend.reporting.dto;

import com.sealease.backend.reporting.dto.AuditLogEntryResponse.UserRef;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The security dashboard for the last {@code hours} hours ([from, to)). Current state (locks, sessions)
 * is as of {@code to}.
 *
 * @param bucket            "HOUR" or "DAY": what each entry of {@code activity} covers (UTC)
 * @param activity          every hour or day of the period, oldest first, including quiet ones
 * @param failedByAccount   the accounts with the most failed sign-ins (password guessing on one account)
 * @param failedByIp        the addresses with the most failed sign-ins (one source trying many accounts)
 * @param sensitiveAccess   staff who opened personal documents or took data out, most active first
 * @param privilegedChanges latest changes to access (roles, staff, suspensions) and platform settings
 * @param securityEvents    latest lockouts, stolen-token detections and password changes
 */
public record SecurityOverview(
		int hours,
		Instant from,
		Instant to,
		Totals totals,
		String bucket,
		List<Activity> activity,
		List<LockedAccount> lockedAccounts,
		List<FailedByAccount> failedByAccount,
		List<FailedByIp> failedByIp,
		List<StaffAccess> sensitiveAccess,
		List<AuditLogEntryResponse> privilegedChanges,
		List<AuditLogEntryResponse> securityEvents) {

	/**
	 * @param activeSessions sessions open right now
	 * @param signedInUsers  people those sessions belong to
	 */
	public record Totals(long signIns, long failedSignIns, long lockouts, long stolenTokenDetections,
			long passwordResets, long passwordChanges, long activeSessions, long signedInUsers,
			long sensitiveAccess, long privilegedChanges) {
	}

	/** @param start the UTC start of the hour or day */
	public record Activity(LocalDateTime start, long signIns, long failedSignIns) {
	}

	public record LockedAccount(UserRef account, Instant lockedUntil, int failedAttempts) {
	}

	public record FailedByAccount(UserRef account, long attempts, Instant latest) {
	}

	public record FailedByIp(String ipAddress, long attempts, Instant latest) {
	}

	/** Counts per kind of access in the period. */
	public record StaffAccess(UserRef staff, long kycDocuments, long kycSubmissions, long bankProofs,
			long ticketAttachments, long reports, long auditExports, Instant latest) {

		public long total() {
			return kycDocuments + kycSubmissions + bankProofs + ticketAttachments + reports + auditExports;
		}
	}

}
