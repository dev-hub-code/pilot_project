package com.sealease.backend.user.service;

import java.util.UUID;

/**
 * Prevents staff from acting on accounts more privileged than themselves (e.g. an ADMIN suspending
 * the SUPER_ADMIN). Implemented by the role module; declared here so the user module does not
 * depend on it.
 */
public interface AuthorityGuard {

	/** @throws com.sealease.backend.common.exception.BusinessException if the actor lacks any permission the target holds */
	void requireActorCovers(UUID actorId, UUID targetUserId);

}
