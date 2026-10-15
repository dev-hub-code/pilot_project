package com.sealease.backend.reporting.dto;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditCategory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One audit log entry for staff with AUDIT_VIEW.
 *
 * @param actor   who did it; {@code null} for anonymous visitors and the system
 * @param subject the account the entry is about, when its entity is a user (e.g. whose sign-in failed)
 */
public record AuditLogEntryResponse(
		UUID id,
		Instant createdAt,
		AuditAction action,
		AuditCategory category,
		UserRef actor,
		String entityType,
		String entityId,
		UserRef subject,
		String ipAddress,
		String userAgent,
		String correlationId,
		Map<String, Object> oldValue,
		Map<String, Object> newValue) {

	/** A person named in the log; {@code email} is null if the account no longer exists. */
	public record UserRef(UUID id, String email, String name) {
	}

}
