package com.sealease.backend.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One stored audit entry, as read back from the log.
 *
 * @param actorUserId who performed the action; {@code null} for anonymous or system actions
 * @param oldValue    the fields before the change, or {@code null}
 * @param newValue    the fields after the change, or {@code null}
 */
public record AuditEntry(
		UUID id,
		UUID actorUserId,
		AuditAction action,
		String entityType,
		String entityId,
		Map<String, Object> oldValue,
		Map<String, Object> newValue,
		String ipAddress,
		String userAgent,
		String correlationId,
		Instant createdAt) {

	public AuditCategory category() {
		return AuditCategory.of(action);
	}

}
