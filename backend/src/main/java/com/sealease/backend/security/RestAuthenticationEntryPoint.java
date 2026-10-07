package com.sealease.backend.security;

import com.sealease.backend.common.api.ApiErrorFactory;
import com.sealease.backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns the standard JSON error for unauthenticated requests instead of a redirect or HTML page.
 * A presented-but-rejected token (bad signature, expired, revoked session) yields
 * {@code INVALID_TOKEN} so clients know to refresh; the rejection reason is not disclosed.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ApiErrorFactory errors;

	public RestAuthenticationEntryPoint(ApiErrorFactory errors) {
		this.errors = errors;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		if (authException instanceof InvalidBearerTokenException) {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
			errors.write(ErrorCode.INVALID_TOKEN, "Access token is invalid or expired", request, response);
			return;
		}
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		errors.write(ErrorCode.UNAUTHORIZED, "Authentication required", request, response);
	}

}
