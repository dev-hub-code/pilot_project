package com.sealease.backend.auth.entity;

public enum SessionRevocationReason {
	LOGOUT,
	LOGOUT_ALL,
	PASSWORD_CHANGED,
	REFRESH_TOKEN_REUSE,
	AUTHORITIES_CHANGED,
	ACCOUNT_INACTIVE
}
