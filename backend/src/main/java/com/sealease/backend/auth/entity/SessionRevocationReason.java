package com.sealease.backend.auth.entity;

public enum SessionRevocationReason {
	LOGOUT,
	LOGOUT_ALL,
	PASSWORD_CHANGED,
	/** An administrator issued a temporary password. */
	PASSWORD_RESET,
	REFRESH_TOKEN_REUSE,
	AUTHORITIES_CHANGED,
	ACCOUNT_INACTIVE
}
