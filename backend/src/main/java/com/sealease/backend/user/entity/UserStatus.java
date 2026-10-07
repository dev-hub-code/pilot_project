package com.sealease.backend.user.entity;

public enum UserStatus {

	/** May sign in and use the platform. */
	ACTIVE,
	/** Temporarily blocked by an administrator; reversible. */
	SUSPENDED,
	/** Permanently closed. */
	DISABLED

}
