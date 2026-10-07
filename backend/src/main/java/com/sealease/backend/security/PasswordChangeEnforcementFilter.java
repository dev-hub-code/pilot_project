package com.sealease.backend.security;

import com.sealease.backend.common.api.ApiErrorFactory;
import com.sealease.backend.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * A user signed in with a temporary password may only replace it (and read who they are, or sign
 * out) until they do: every other API call is refused with {@code PASSWORD_CHANGE_REQUIRED}. The
 * next token, issued after the change, no longer carries the claim.
 */
class PasswordChangeEnforcementFilter extends OncePerRequestFilter {

	private static final Set<String> ALLOWED = Set.of("/api/v1/auth/password", "/api/v1/auth/me", "/api/v1/auth/logout",
			"/api/v1/auth/logout-all", "/api/v1/auth/refresh");

	private final ApiErrorFactory errors;

	PasswordChangeEnforcementFilter(ApiErrorFactory errors) {
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
				&& Boolean.TRUE.equals(token.getToken().getClaimAsBoolean(PlatformClaims.PASSWORD_CHANGE_REQUIRED))
				&& !ALLOWED.contains(request.getRequestURI())) {
			errors.write(ErrorCode.PASSWORD_CHANGE_REQUIRED,
					"Choose a new password to replace your temporary one before continuing", request, response);
			return;
		}
		chain.doFilter(request, response);
	}

}
