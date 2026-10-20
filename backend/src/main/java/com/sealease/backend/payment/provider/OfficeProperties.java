package com.sealease.backend.payment.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Paying and collecting cash at the company's office.
 *
 * @param address    where investors come to pay or collect cash
 * @param hours      when the office takes and hands out cash
 * @param holdWindow how long an order waits for the investor to pay at the office before it lapses
 */
@ConfigurationProperties(prefix = "app.office")
public record OfficeProperties(
		@DefaultValue("") String address,
		@DefaultValue("Monday to Friday, 10:00 to 17:00") String hours,
		@DefaultValue("3d") Duration holdWindow) {
}
