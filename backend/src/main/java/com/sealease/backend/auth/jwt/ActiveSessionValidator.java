package com.sealease.backend.auth.jwt;

import com.sealease.backend.auth.service.SessionService;
import com.sealease.backend.security.PlatformClaims;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Rejects access tokens whose session has been revoked or has expired. This is what makes logout,
 * password change and authority changes take effect immediately instead of when the token expires.
 * Costs one primary-key lookup per authenticated request.
 */
class ActiveSessionValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error INACTIVE = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
			"The session for this token is no longer active", null);

	private final SessionService sessions;

	ActiveSessionValidator(SessionService sessions) {
		this.sessions = sessions;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		String sid = token.getClaimAsString(PlatformClaims.SESSION_ID);
		if (sid == null) {
			return OAuth2TokenValidatorResult.failure(INACTIVE);
		}
		try {
			return sessions.isActive(UUID.fromString(sid)) ? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(INACTIVE);
		}
		catch (IllegalArgumentException malformed) {
			return OAuth2TokenValidatorResult.failure(INACTIVE);
		}
	}

}
