package com.sealease.backend.auth.dto;

import java.time.Instant;

/**
 * Tokens for the Next.js server, which stores them in HttpOnly cookies. Browsers never see this body.
 */
public record AuthTokensResponse(
		String tokenType,
		String accessToken,
		Instant accessTokenExpiresAt,
		String refreshToken,
		Instant refreshTokenExpiresAt) {

	@Override
	public String toString() {
		return "AuthTokensResponse[accessTokenExpiresAt=" + accessTokenExpiresAt + ", refreshTokenExpiresAt="
				+ refreshTokenExpiresAt + "]";
	}

}
