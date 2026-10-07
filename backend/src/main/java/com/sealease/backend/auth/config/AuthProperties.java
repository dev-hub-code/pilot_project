package com.sealease.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param defaultRole role granted on self-registration
 * @param rateLimit   per-instance throttles; defaults are defined in application.yaml
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
		@DefaultValue("INVESTOR") String defaultRole,
		RateLimits rateLimit) {

	public record RateLimits(
			Limit loginPerIp,
			Limit loginPerEmail,
			Limit registerPerIp,
			Limit refreshPerIp,
			Limit passwordChangePerUser) {
	}

	public record Limit(int requests, Duration window) {
	}

}
