package com.sealease.backend.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param maxFailedAttempts consecutive failures that lock the account
 * @param lockDuration      how long the account stays locked
 */
@ConfigurationProperties(prefix = "app.auth.lockout")
public record LockoutProperties(
		@DefaultValue("5") int maxFailedAttempts,
		@DefaultValue("15m") Duration lockDuration) {
}
