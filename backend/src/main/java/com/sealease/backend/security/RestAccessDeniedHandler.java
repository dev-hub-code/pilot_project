package com.sealease.backend.security;

import com.sealease.backend.common.api.ApiErrorFactory;
import com.sealease.backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns the standard JSON error when an authenticated caller lacks the required authority. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final ApiErrorFactory errors;

	public RestAccessDeniedHandler(ApiErrorFactory errors) {
		this.errors = errors;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		errors.write(ErrorCode.FORBIDDEN, "Access denied", request, response);
	}

}
