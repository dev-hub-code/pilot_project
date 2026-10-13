package com.sealease.backend.notification.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled whether notifications are also emailed (needs {@code spring.mail.*} pointing at an SMTP server)
 * @param from    the sender address, e.g. {@code SeaLease <no-reply@sealease.com>}
 * @param appUrl  the web app's public address, prefixed to notification links in emails
 */
@ConfigurationProperties(prefix = "app.notifications.email")
public record NotificationEmailProperties(
		@DefaultValue("false") boolean enabled,
		@DefaultValue("SeaLease <no-reply@sealease.local>") String from,
		@DefaultValue("http://localhost:3000") String appUrl) {
}
