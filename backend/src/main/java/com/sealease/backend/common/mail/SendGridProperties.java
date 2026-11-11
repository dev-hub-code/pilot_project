package com.sealease.backend.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * SendGrid's Web API (app.mail.sendgrid). When the API key is set, every email goes out through
 * {@code POST /v3/mail/send} instead of SMTP; without it, the {@code spring.mail.*} SMTP server is used (Mailpit locally).
 *
 * @param apiKey  an API key with the Mail Send permission (SG.…)
 * @param baseUrl the API's address
 * @param timeout connect and read timeout for each call
 */
@ConfigurationProperties(prefix = "app.mail.sendgrid")
public record SendGridProperties(
		@DefaultValue("") String apiKey,
		@DefaultValue("https://api.sendgrid.com") URI baseUrl,
		@DefaultValue("10s") Duration timeout) {

	public boolean enabled() {
		return !apiKey.isBlank();
	}

}
