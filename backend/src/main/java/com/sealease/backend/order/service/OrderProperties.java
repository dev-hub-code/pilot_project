package com.sealease.backend.order.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param paymentWindow how long checkout holds capacity while waiting for payment
 * @param expiryBatchSize orders expired per sweep
 */
@ConfigurationProperties(prefix = "app.orders")
public record OrderProperties(
		@DefaultValue("30m") Duration paymentWindow,
		@DefaultValue("100") int expiryBatchSize) {
}
