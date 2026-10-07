package com.sealease.backend.support;

import com.sealease.backend.security.PlatformClaims;
import com.sealease.backend.security.PlatformJwtAuthenticationConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.List;
import java.util.UUID;

/**
 * Builds authenticated requests for slice tests, shaped like real platform access tokens.
 * Authorities are derived from the claims by the production converter, exactly as at runtime.
 */
public final class TestJwts {

	private static final PlatformJwtAuthenticationConverter CONVERTER = new PlatformJwtAuthenticationConverter();

	private TestJwts() {
	}

	public static JwtRequestPostProcessor userWith(UUID userId, String... permissions) {
		return SecurityMockMvcRequestPostProcessors.jwt()
			.jwt(jwt -> jwt.subject(userId.toString())
				.claim(PlatformClaims.USER_ID, userId.toString())
				.claim(PlatformClaims.SESSION_ID, UUID.randomUUID().toString())
				.claim(PlatformClaims.ROLES, List.of())
				.claim(PlatformClaims.PERMISSIONS, List.of(permissions)))
			.authorities(jwt -> List.<GrantedAuthority>copyOf(CONVERTER.convert(jwt).getAuthorities()));
	}

	public static JwtRequestPostProcessor userWith(String... permissions) {
		return userWith(UUID.randomUUID(), permissions);
	}

}
