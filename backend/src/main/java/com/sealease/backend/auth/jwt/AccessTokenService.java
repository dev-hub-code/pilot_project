package com.sealease.backend.auth.jwt;

import com.nimbusds.jose.jwk.RSAKey;
import com.sealease.backend.auth.config.JwtProperties;
import com.sealease.backend.role.dto.UserAuthorities;
import com.sealease.backend.security.PlatformClaims;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Issues short-lived RS256 access tokens carrying only identifiers and authorities (no PII). */
@Service
public class AccessTokenService {

	private final JwtEncoder encoder;
	private final JwtProperties properties;
	private final String keyId;
	private final Clock clock;

	public AccessTokenService(JwtEncoder encoder, JwtProperties properties, RSAKey jwtSigningKey, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.keyId = jwtSigningKey.getKeyID();
		this.clock = clock;
	}

	public IssuedAccessToken issue(UUID userId, UUID sessionId, UserAuthorities authorities) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.id(UUID.randomUUID().toString())
			.issuer(properties.issuer())
			.audience(List.of(properties.audience()))
			.subject(userId.toString())
			.issuedAt(now)
			.notBefore(now)
			.expiresAt(expiresAt)
			.claim(PlatformClaims.USER_ID, userId.toString())
			.claim(PlatformClaims.SESSION_ID, sessionId.toString())
			.claim(PlatformClaims.ROLES, List.copyOf(authorities.roles()))
			.claim(PlatformClaims.PERMISSIONS, List.copyOf(authorities.permissions()))
			.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).type("JWT").build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedAccessToken(token, expiresAt);
	}

	public record IssuedAccessToken(String value, Instant expiresAt) {

		@Override
		public String toString() {
			return "IssuedAccessToken[expiresAt=" + expiresAt + "]";
		}

	}

}
