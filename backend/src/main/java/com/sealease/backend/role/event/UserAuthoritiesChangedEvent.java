package com.sealease.backend.role.event;

import java.util.Set;
import java.util.UUID;

/**
 * Published (synchronously, inside the changing transaction) when the effective roles or
 * permissions of users change. Access tokens embed authorities, so listeners revoke the affected
 * users' sessions to force re-authentication with fresh claims.
 */
public record UserAuthoritiesChangedEvent(Set<UUID> userIds) {

	public UserAuthoritiesChangedEvent {
		userIds = Set.copyOf(userIds);
	}

}
