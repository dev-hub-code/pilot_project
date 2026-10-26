package com.sealease.backend.user.entity;

public enum UserStatus {

	/** May sign in and use the platform. */
	ACTIVE,
	/** Temporarily blocked by an administrator; reversible. */
	SUSPENDED,
	/** Closed by a super administrator; only a super administrator can enable it again. */
	DISABLED

}
