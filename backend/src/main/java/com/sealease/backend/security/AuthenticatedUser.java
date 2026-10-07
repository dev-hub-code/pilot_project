package com.sealease.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The caller of the current request, as asserted by a verified access token. Declare it as a
 * controller method parameter to receive it (see {@link AuthenticatedUserArgumentResolver}).
 */
public record AuthenticatedUser(UUID userId, UUID sessionId, Set<String> roles, Set<String> permissions) {

	public AuthenticatedUser {
		roles = Set.copyOf(roles);
		permissions = Set.copyOf(permissions);
	}

	public static Optional<AuthenticatedUser> from(Authentication authentication) {
		if (!(authentication instanceof JwtAuthenticationToken token)) {
			return Optional.empty();
		}
		Jwt jwt = token.getToken();
		return Optional.of(new AuthenticatedUser(
				UUID.fromString(jwt.getSubject()),
				UUID.fromString(jwt.getClaimAsString(PlatformClaims.SESSION_ID)),
				Set.copyOf(listClaim(jwt, PlatformClaims.ROLES)),
				Set.copyOf(listClaim(jwt, PlatformClaims.PERMISSIONS))));
	}

	public boolean hasPermission(String permission) {
		return permissions.contains(permission);
	}

	private static List<String> listClaim(Jwt jwt, String claim) {
		List<String> values = jwt.getClaimAsStringList(claim);
		return values == null ? List.of() : values;
	}

}
