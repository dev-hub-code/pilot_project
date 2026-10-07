package com.sealease.backend.audit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One audit entry. {@code oldValue}/{@code newValue} hold only the fields that matter for the
 * action and must never contain secrets (passwords, hashes, tokens, full bank/KYC numbers).
 *
 * @param actorUserId who performed the action; {@code null} for anonymous or system actions
 */
public record AuditRecord(
		UUID actorUserId,
		AuditAction action,
		String entityType,
		String entityId,
		Map<String, ?> oldValue,
		Map<String, ?> newValue) {

	public AuditRecord {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(entityType, "entityType");
	}

	public static AuditRecord of(UUID actorUserId, AuditAction action, String entityType, Object entityId) {
		return new AuditRecord(actorUserId, action, entityType, entityId == null ? null : entityId.toString(),
				null, null);
	}

	public AuditRecord withOldValue(Map<String, ?> value) {
		return new AuditRecord(actorUserId, action, entityType, entityId, value, newValue);
	}

	public AuditRecord withNewValue(Map<String, ?> value) {
		return new AuditRecord(actorUserId, action, entityType, entityId, oldValue, value);
	}

}
