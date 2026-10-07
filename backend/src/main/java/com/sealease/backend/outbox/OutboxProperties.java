package com.sealease.backend.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param batchSize    events claimed per relay run
 * @param sendTimeout  how long the relay waits for the broker to acknowledge one event
 */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
		@DefaultValue("100") int batchSize,
		@DefaultValue("10s") Duration sendTimeout) {
}
