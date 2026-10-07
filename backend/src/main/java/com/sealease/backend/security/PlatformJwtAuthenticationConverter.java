package com.sealease.backend.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Maps the {@code permissions} claim to authorities of the same name, so endpoints authorise with
 * {@code hasAuthority('WITHDRAWAL_APPROVE')}.
 *
 * <p>Role names are deliberately <b>not</b> turned into authorities: authorization is
 * permission-based, and a {@code ROLE_<name>} authority could collide with a permission such as
 * {@code ROLE_VIEW} if an administrator created a role called {@code VIEW}.
 */
public class PlatformJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		List<String> permissions = jwt.getClaimAsStringList(PlatformClaims.PERMISSIONS);
		List<GrantedAuthority> authorities = permissions == null ? List.of()
				: permissions.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
		return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
	}

}
