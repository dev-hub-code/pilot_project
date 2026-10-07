package com.sealease.backend.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.stream.Stream;

/**
 * Kafka infrastructure shared by all modules.
 *
 * <p>Consumers get bounded exponential-backoff retries, after which the failed record is published
 * to {@code <topic>.dlt} with the original headers plus exception metadata. Spring Boot wires the
 * {@link CommonErrorHandler} bean into the default listener container factory automatically.
 *
 * <p>Retries mean at-least-once delivery: every consumer must be idempotent (keyed on the event
 * ID), which the event-processing infrastructure introduced with the outbox enforces.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

	@Bean
	CommonErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaOperations, PlatformKafkaProperties properties) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
				(record, ex) -> new TopicPartition(KafkaTopics.deadLetterOf(record.topic()), -1));

		PlatformKafkaProperties.Retry retry = properties.retry();
		ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.maxRetries());
		backOff.setInitialInterval(retry.initialInterval().toMillis());
		backOff.setMultiplier(retry.multiplier());
		backOff.setMaxInterval(retry.maxInterval().toMillis());

		return new DefaultErrorHandler(recoverer, backOff);
	}

	@Bean
	@ConditionalOnProperty(prefix = "app.kafka", name = "create-topics", havingValue = "true")
	KafkaAdmin.NewTopics platformTopics(PlatformKafkaProperties properties) {
		NewTopic[] topics = KafkaTopics.ALL.stream()
			.flatMap(topic -> Stream.of(topic, KafkaTopics.deadLetterOf(topic)))
			.map(name -> TopicBuilder.name(name)
				.partitions(properties.partitions())
				.replicas(properties.replicationFactor())
				.build())
			.toArray(NewTopic[]::new);
		return new KafkaAdmin.NewTopics(topics);
	}

}
