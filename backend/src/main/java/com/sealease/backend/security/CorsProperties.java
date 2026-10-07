package com.sealease.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * CORS policy. In the intended deployment the browser talks only to the Next.js origin, which
 * proxies {@code /api/v1/**} server-side, so this list is normally empty in production and CORS
 * is effectively closed.
 */
@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(
		@DefaultValue List<String> allowedOrigins,
		@DefaultValue({ "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS" }) List<String> allowedMethods,
		@DefaultValue({ "Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id" }) List<String> allowedHeaders,
		@DefaultValue({ "X-Correlation-Id", "X-Request-Id" }) List<String> exposedHeaders,
		@DefaultValue("1h") Duration maxAge) {
}
