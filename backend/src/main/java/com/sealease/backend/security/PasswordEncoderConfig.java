package com.sealease.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Argon2id password hashing (OWASP baseline: 19 MiB memory, 2 iterations, parallelism 1).
 *
 * <p>Hashes are stored with an {@code {id}} prefix through {@link DelegatingPasswordEncoder}, so
 * parameters or algorithms can be upgraded later: old hashes keep verifying and are re-hashed
 * transparently on the next successful login.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

	private static final String CURRENT = "argon2id";

	@Bean
	PasswordEncoder passwordEncoder() {
		Map<String, PasswordEncoder> encoders = Map.of(
				CURRENT, new Argon2PasswordEncoder(16, 32, 1, 19_456, 2),
				"bcrypt", new BCryptPasswordEncoder(12));
		return new DelegatingPasswordEncoder(CURRENT, encoders);
	}

}
