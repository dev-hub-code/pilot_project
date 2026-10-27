package com.sealease.backend.auth.entity;

public enum SessionRevocationReason {
	LOGOUT,
	LOGOUT_ALL,
	PASSWORD_CHANGED,
	/** An administrator issued a temporary password. */
	PASSWORD_RESET,
	REFRESH_TOKEN_REUSE,
	AUTHORITIES_CHANGED,
	ACCOUNT_INACTIVE,
	/** The user signed in again (on another device or browser): one session per user. */
	SIGNED_IN_ELSEWHERE
}
