package com.sealease.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * First super-administrator, created at startup only while no user holds {@code role}.
 * Supply via environment variables for the first deployment, then remove them.
 */
@ConfigurationProperties(prefix = "app.bootstrap.super-admin")
public record BootstrapProperties(
		String email,
		String password,
		@DefaultValue("Platform") String firstName,
		@DefaultValue("Administrator") String lastName,
		@DefaultValue("SUPER_ADMIN") String role) {

	public boolean isConfigured() {
		return email != null && !email.isBlank() && password != null && !password.isBlank();
	}

	@Override
	public String toString() {
		return "BootstrapProperties[email=" + email + ", role=" + role + "]";
	}

}
