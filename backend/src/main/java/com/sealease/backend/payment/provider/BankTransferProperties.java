package com.sealease.backend.payment.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param verificationWindow how long an order waits for finance to verify a bank payment once the
 *                           investor has submitted its details
 */
@ConfigurationProperties(prefix = "app.payments.bank-transfer")
public record BankTransferProperties(@DefaultValue("7d") Duration verificationWindow) {
}
