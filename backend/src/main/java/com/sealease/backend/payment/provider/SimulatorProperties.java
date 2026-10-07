package com.sealease.backend.payment.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Card-gateway simulator for development and tests. Must stay disabled in production.
 *
 * @param webhookSecret HMAC-SHA256 key shared with the (simulated) gateway
 */
@ConfigurationProperties(prefix = "app.payments.simulator")
public record SimulatorProperties(@DefaultValue("false") boolean enabled, String webhookSecret) {
}
