package com.sealease.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param defaultRole       role granted on self-registration
 * @param rateLimit         per-instance throttles; defaults are defined in application.yaml
 * @param emailVerification confirmation of a self-registered account's email address
 * @param passwordReset     "forgot password" by a code emailed to the account's address
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
		@DefaultValue("INVESTOR") String defaultRole,
		RateLimits rateLimit,
		@DefaultValue EmailVerification emailVerification,
		@DefaultValue PasswordReset passwordReset) {

	public record RateLimits(
			Limit loginPerIp,
			Limit loginPerEmail,
			Limit registerPerIp,
			Limit refreshPerIp,
			Limit passwordChangePerUser,
			Limit verificationEmailPerIp,
			Limit verificationEmailPerAddress,
			Limit verificationCodePerIp,
			Limit passwordResetPerIp,
			Limit passwordResetPerAddress,
			Limit passwordResetCodePerIp) {
	}

	public record Limit(int requests, Duration window) {
	}

	/**
	 * @param required whether new accounts must confirm their email before signing in; when off they count as confirmed
	 * @param linkTtl  how long an emailed verification link (and the code in the same email) works
	 * @param maxCodeAttempts wrong codes allowed per email before a new one must be requested
	 * @param from     the sender address of verification emails
	 * @param appUrl   the web app's public address; the link opens {@code <appUrl>/verify-email?token=...}
	 */
	public record EmailVerification(
			@DefaultValue("true") boolean required,
			@DefaultValue("24h") Duration linkTtl,
			@DefaultValue("5") int maxCodeAttempts,
			@DefaultValue("SeaLease <no-reply@sealease.local>") String from,
			@DefaultValue("http://localhost:3000") String appUrl) {
	}

	/**
	 * @param codeTtl     how long an emailed reset code works
	 * @param maxAttempts wrong codes allowed before a new one must be requested
	 */
	public record PasswordReset(
			@DefaultValue("15m") Duration codeTtl,
			@DefaultValue("5") int maxAttempts) {
	}

}
