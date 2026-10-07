package com.sealease.backend.security;

/** Custom claim names in platform access tokens. Standard claims: sub (= user id), iat, exp, jti, iss, aud. */
public final class PlatformClaims {

	public static final String USER_ID = "user_id";
	public static final String SESSION_ID = "sid";
	public static final String ROLES = "roles";
	public static final String PERMISSIONS = "permissions";

	private PlatformClaims() {
	}

}
