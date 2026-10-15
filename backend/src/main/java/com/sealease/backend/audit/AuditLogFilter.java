package com.sealease.backend.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Narrows a search of the audit log; every field is optional and they combine with AND.
 *
 * @param from inclusive
 * @param to   exclusive
 */
public record AuditLogFilter(
		AuditCategory category,
		AuditAction action,
		UUID actorUserId,
		String entityType,
		String entityId,
		String ipAddress,
		Instant from,
		Instant to) {

	public static AuditLogFilter none() {
		return new AuditLogFilter(null, null, null, null, null, null, null, null);
	}

}
