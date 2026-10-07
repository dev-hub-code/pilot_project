package com.sealease.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Container Investment Platform - modular monolith entry point.
 *
 * <p>The in-memory {@code UserDetailsService} auto-configuration is excluded: authentication is
 * provided by the platform's own user store and JWT (RS256) issuance, never by a generated
 * default user.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
