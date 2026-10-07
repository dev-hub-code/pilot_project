package com.sealease.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param refreshTokenTtl  idle timeout: a refresh token not used within this period expires
 * @param maxLifetime      absolute session lifetime; re-login required afterwards
 * @param reuseGracePeriod a just-rotated refresh token presented again within this window is
 *                         rejected without revoking the session (benign concurrent refreshes from
 *                         several browser tabs); outside it, reuse is treated as theft
 */
@ConfigurationProperties(prefix = "app.auth.session")
public record SessionProperties(
		@DefaultValue("7d") Duration refreshTokenTtl,
		@DefaultValue("30d") Duration maxLifetime,
		@DefaultValue("10s") Duration reuseGracePeriod) {
}
