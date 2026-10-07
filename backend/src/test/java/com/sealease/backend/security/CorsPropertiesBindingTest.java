package com.sealease.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesBindingTest {

	@Test
	void emptyOriginListBindsToNoOrigins() {
		// Production sets APP_CORS_ALLOWED_ORIGINS to empty: that must mean "no origins", not [""].
		CorsProperties properties = bind(Map.of("app.security.cors.allowed-origins", ""));
		assertThat(properties.allowedOrigins()).isEmpty();
	}

	@Test
	void commaSeparatedOriginsAndDefaults() {
		CorsProperties properties = bind(
				Map.of("app.security.cors.allowed-origins", "https://a.example.com,https://b.example.com"));
		assertThat(properties.allowedOrigins()).containsExactly("https://a.example.com", "https://b.example.com");
		assertThat(properties.allowedHeaders()).contains("Authorization", "Idempotency-Key", "X-Correlation-Id");
		assertThat(properties.exposedHeaders()).contains("X-Correlation-Id", "X-Request-Id");
	}

	private static CorsProperties bind(Map<String, String> source) {
		return new Binder(new MapConfigurationPropertySource(source))
			.bindOrCreate("app.security.cors", CorsProperties.class);
	}

}
