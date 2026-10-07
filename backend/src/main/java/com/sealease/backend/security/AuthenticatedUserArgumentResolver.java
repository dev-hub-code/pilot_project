package com.sealease.backend.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link AuthenticatedUser} controller parameters from the security context. */
public class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType().equals(AuthenticatedUser.class);
	}

	@Override
	public AuthenticatedUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		return AuthenticatedUser.from(SecurityContextHolder.getContext().getAuthentication())
			.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authentication required"));
	}

}
