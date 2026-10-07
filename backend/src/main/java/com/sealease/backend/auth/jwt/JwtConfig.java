package com.sealease.backend.auth.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sealease.backend.auth.config.JwtProperties;
import com.sealease.backend.auth.service.SessionService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.util.Objects;

/**
 * RS256 JWT infrastructure. The private key signs; only the public key verifies, and it is
 * published at {@code /api/v1/auth/jwks} so other parties (the Next.js server, future services)
 * can verify tokens without sharing a secret.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	@Bean
	RSAKey jwtSigningKey(JwtProperties properties) throws JOSEException {
		RsaKeyLoader.RsaKeyPair pair = RsaKeyLoader.load(properties.privateKey(), properties.publicKey());
		RSAKey withoutId = new RSAKey.Builder(pair.publicKey()).privateKey(pair.privateKey()).build();
		// Key id = RFC 7638 thumbprint, so it changes automatically when the key is rotated.
		return new RSAKey.Builder(withoutId)
			.keyID(withoutId.computeThumbprint().toString())
			.keyUse(KeyUse.SIGNATURE)
			.algorithm(JWSAlgorithm.RS256)
			.build();
	}

	@Bean
	JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
	}

	/** Public JWK set; never contains private key material. */
	@Bean
	JWKSet publicJwkSet(RSAKey jwtSigningKey) {
		return new JWKSet(jwtSigningKey.toPublicJWK());
	}

	@Bean
	JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties, SessionService sessions, Clock clock)
			throws JOSEException {
		Objects.requireNonNull(properties.issuer(), "app.auth.jwt.issuer must be set");
		Objects.requireNonNull(properties.audience(), "app.auth.jwt.audience must be set");

		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey())
			.signatureAlgorithm(SignatureAlgorithm.RS256)
			.build();

		JwtTimestampValidator timestamps = new JwtTimestampValidator(properties.clockSkew());
		timestamps.setClock(clock);
		OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
				timestamps,
				new JwtIssuerValidator(properties.issuer()),
				new JwtAudienceValidator(properties.audience()),
				new JwtClaimValidator<>(JwtClaimNames.JTI, Objects::nonNull),
				new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull),
				new ActiveSessionValidator(sessions));
		decoder.setJwtValidator(validator);
		return decoder;
	}

}
