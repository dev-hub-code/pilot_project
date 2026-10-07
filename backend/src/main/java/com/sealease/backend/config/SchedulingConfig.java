package com.sealease.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background jobs (outbox relay, order expiry). Every job is safe to run on several instances at
 * once: the relay claims rows with {@code FOR UPDATE SKIP LOCKED}, and expiry locks each order and
 * re-checks it. Integration tests switch scheduling off and run jobs explicitly.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
