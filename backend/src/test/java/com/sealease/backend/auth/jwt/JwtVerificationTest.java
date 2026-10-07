package com.sealease.backend.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sealease.backend.auth.config.JwtProperties;
import com.sealease.backend.auth.service.SessionService;
import com.sealease.backend.role.dto.UserAuthorities;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.security.PlatformClaims;
import com.sealease.backend.security.PlatformJwtAuthenticationConverter;
import com.sealease.backend.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises the production encoder/decoder wiring from {@link JwtConfig} with real RS256 keys. */
class JwtVerificationTest {

	private static final String ISSUER = "https://api.test";
	private static final String AUDIENCE = "sealease-api";

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-07T10:00:00Z"));
	private final SessionService sessions = mock(SessionService.class);
	private final UUID userId = UUID.randomUUID();
	private final UUID sessionId = UUID.randomUUID();
	private final UserAuthorities authorities = new UserAuthorities(Set.of("FINANCE"),
			Set.of("WITHDRAWAL_APPROVE", "FINANCE_VIEW"));

	private RSAKey signingKey;
	private AccessTokenService tokens;
	private JwtDecoder decoder;

	@BeforeEach
	void setUp() throws Exception {
		JwtProperties properties = properties(ISSUER, AUDIENCE);
		JwtConfig config = new JwtConfig();
		signingKey = config.jwtSigningKey(properties);
		tokens = new AccessTokenService(config.jwtEncoder(signingKey), properties, signingKey, clock);
		decoder = config.jwtDecoder(signingKey, properties, sessions, clock);
		when(sessions.isActive(any())).thenReturn(true);
	}

	@Test
	void issuedTokenVerifiesAndCarriesMinimalClaims() {
		Jwt jwt = decoder.decode(tokens.issue(userId, sessionId, authorities).value());

		assertThat(jwt.getSubject()).isEqualTo(userId.toString());
		assertThat(jwt.getClaimAsString(PlatformClaims.USER_ID)).isEqualTo(userId.toString());
		assertThat(jwt.getClaimAsString(PlatformClaims.SESSION_ID)).isEqualTo(sessionId.toString());
		assertThat(jwt.getClaimAsStringList(PlatformClaims.PERMISSIONS))
			.containsExactlyInAnyOrder("WITHDRAWAL_APPROVE", "FINANCE_VIEW");
		assertThat(jwt.getId()).isNotBlank();
		assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", signingKey.getKeyID());
		assertThat(jwt.getClaims()).doesNotContainKeys("email", "first_name", "last_name");
		assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
	}

	@Test
	void permissionsBecomeAuthoritiesButRolesDoNot() {
		Jwt jwt = decoder.decode(tokens.issue(userId, sessionId, authorities).value());
		var authentication = new PlatformJwtAuthenticationConverter().convert(jwt);

		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("WITHDRAWAL_APPROVE", "FINANCE_VIEW");
		AuthenticatedUser user = AuthenticatedUser.from(authentication).orElseThrow();
		assertThat(user.userId()).isEqualTo(userId);
		assertThat(user.sessionId()).isEqualTo(sessionId);
		assertThat(user.roles()).containsExactly("FINANCE");
	}

	@Test
	void expiredTokenIsRejected() {
		String token = tokens.issue(userId, sessionId, authorities).value();
		clock.advance(Duration.ofMinutes(15).plusSeconds(31));
		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class).hasMessageContaining("expired");
	}

	@Test
	void tokenWithinClockSkewIsAccepted() {
		String token = tokens.issue(userId, sessionId, authorities).value();
		clock.advance(Duration.ofMinutes(15).plusSeconds(20));
		assertThat(decoder.decode(token).getSubject()).isEqualTo(userId.toString());
	}

	@Test
	void revokedSessionIsRejected() {
		String token = tokens.issue(userId, sessionId, authorities).value();
		when(sessions.isActive(sessionId)).thenReturn(false);
		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class)
			.hasMessageContaining("no longer active");
	}

	@Test
	void wrongIssuerOrAudienceIsRejected() throws Exception {
		JwtConfig config = new JwtConfig();
		AccessTokenService otherIssuer = new AccessTokenService(config.jwtEncoder(signingKey),
				properties("https://evil.test", AUDIENCE), signingKey, clock);
		AccessTokenService otherAudience = new AccessTokenService(config.jwtEncoder(signingKey),
				properties(ISSUER, "another-api"), signingKey, clock);

		assertThatThrownBy(() -> decoder.decode(otherIssuer.issue(userId, sessionId, authorities).value()))
			.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> decoder.decode(otherAudience.issue(userId, sessionId, authorities).value()))
			.isInstanceOf(JwtException.class);
	}

	@Test
	void tamperedPayloadIsRejected() {
		String[] parts = tokens.issue(userId, sessionId, authorities).value().split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]))
			.replace("WITHDRAWAL_APPROVE", "FINANCE_ADJUST");
		String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes())
				+ "." + parts[2];
		assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
	}

	@Test
	void tokenSignedByAnotherKeyIsRejected() throws Exception {
		RSAKey foreignKey = new RSAKeyGenerator(2048).keyID(signingKey.getKeyID()).generate();
		AccessTokenService forger = new AccessTokenService(new JwtConfig().jwtEncoder(foreignKey),
				properties(ISSUER, AUDIENCE), foreignKey, clock);

		String forged = forger.issue(userId, sessionId, authorities).value();

		assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
	}

	@Test
	void mismatchedKeyPairFailsAtStartup() {
		JwtProperties mismatched = new JwtProperties(ISSUER, AUDIENCE, new ClassPathResource("jwt/test-only-private.pem"),
				new ClassPathResource("jwt/test-only-other-public.pem"), Duration.ofMinutes(15), Duration.ofSeconds(30));
		assertThatThrownBy(() -> new JwtConfig().jwtSigningKey(mismatched)).hasMessageContaining("does not match");
	}

	@Test
	void missingKeysFailAtStartup() {
		JwtProperties missing = new JwtProperties(ISSUER, AUDIENCE, null, null, Duration.ofMinutes(15),
				Duration.ofSeconds(30));
		assertThatThrownBy(() -> new JwtConfig().jwtSigningKey(missing)).hasMessageContaining("not configured");
	}

	@Test
	void unsignedAndSymmetricTokensAreRejected() throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
			.subject(userId.toString())
			.issuer(ISSUER)
			.audience(AUDIENCE)
			.jwtID(UUID.randomUUID().toString())
			.expirationTime(Date.from(clock.instant().plusSeconds(600)))
			.claim(PlatformClaims.SESSION_ID, sessionId.toString())
			.claim(PlatformClaims.PERMISSIONS, List.of("FINANCE_ADJUST"))
			.build();

		String unsigned = new PlainJWT(claims).serialize();
		assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(JwtException.class);

		// Classic algorithm-confusion attack: HMAC "signed" with the public key bytes.
		SignedJWT hmac = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		hmac.sign(new MACSigner(signingKey.toRSAPublicKey().getEncoded()));
		assertThatThrownBy(() -> decoder.decode(hmac.serialize())).isInstanceOf(JwtException.class);
	}

	@Test
	void publicJwkSetContainsNoPrivateMaterial() {
		var jwks = new JwtConfig().publicJwkSet(signingKey).toJSONObject(true);
		assertThat(jwks.toString()).doesNotContain("\"d\"", "\"p\"", "\"q\"").contains(signingKey.getKeyID());
	}

	private static JwtProperties properties(String issuer, String audience) {
		return new JwtProperties(issuer, audience, new ClassPathResource("jwt/test-only-private.pem"),
				new ClassPathResource("jwt/test-only-public.pem"), Duration.ofMinutes(15), Duration.ofSeconds(30));
	}

}
