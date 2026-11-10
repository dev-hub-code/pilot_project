package com.sealease.backend.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A second SMTP server (e.g. Brevo) used when the primary one ({@code spring.mail.*}) cannot send. It shares the
 * primary's {@code spring.mail.properties} (auth, STARTTLS, timeouts). Off while {@code host} is blank.
 */
@ConfigurationProperties(prefix = "app.mail.fallback")
public record FallbackMailProperties(String host, Integer port, String username, String password) {
}
