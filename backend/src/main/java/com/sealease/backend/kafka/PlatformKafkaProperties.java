package com.sealease.backend.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Platform-level Kafka settings. Broker/client settings stay under the standard
 * {@code spring.kafka.*} namespace.
 *
 * @param createTopics declare topics at startup (dev/test); in production topics are provisioned
 *                     by infrastructure and this must be {@code false}
 */
@ConfigurationProperties(prefix = "app.kafka")
public record PlatformKafkaProperties(
		@DefaultValue("false") boolean createTopics,
		@DefaultValue("3") int partitions,
		@DefaultValue("1") short replicationFactor,
		@DefaultValue Retry retry) {

	/**
	 * Consumer retry policy before a record is routed to its dead-letter topic.
	 */
	public record Retry(
			@DefaultValue("4") int maxRetries,
			@DefaultValue("500ms") Duration initialInterval,
			@DefaultValue("2.0") double multiplier,
			@DefaultValue("10s") Duration maxInterval) {
	}

}
