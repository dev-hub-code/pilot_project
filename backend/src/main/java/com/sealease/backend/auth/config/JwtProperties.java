package com.sealease.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Access-token settings. Keys are PEM files referenced by location ({@code file:/run/secrets/...});
 * they are never embedded in source or configuration files.
 *
 * @param privateKey     PKCS#8 RSA private key ({@code -----BEGIN PRIVATE KEY-----}), signs tokens
 * @param publicKey      X.509 RSA public key ({@code -----BEGIN PUBLIC KEY-----}), verifies tokens
 * @param accessTokenTtl lifetime of access tokens; keep short, revocation is session-based
 * @param clockSkew      tolerated clock difference when validating exp/nbf
 */
@ConfigurationProperties(prefix = "app.auth.jwt")
public record JwtProperties(
		String issuer,
		String audience,
		Resource privateKey,
		Resource publicKey,
		@DefaultValue("15m") Duration accessTokenTtl,
		@DefaultValue("30s") Duration clockSkew) {
}
