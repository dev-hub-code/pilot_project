package com.sealease.backend.notification.email;

import com.sealease.backend.notification.repository.NotificationRepository;
import com.sealease.backend.outbox.ProcessedEvents;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.json.JsonMapper;

/** Notification emails, on when {@code app.notifications.email.enabled=true} (SMTP set via {@code spring.mail.*}). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.notifications.email", name = "enabled", havingValue = "true")
class NotificationEmailConfig {

	@Bean
	NotificationEmailSender notificationEmailSender(NotificationRepository notifications, UserAccountService accounts,
			UserProfileService profiles, JavaMailSender mail, NotificationEmailProperties properties) {
		return new NotificationEmailSender(notifications, accounts, profiles, mail, properties);
	}

	@Bean
	NotificationEmailListener notificationEmailListener(NotificationEmailSender sender, ProcessedEvents processedEvents,
			JsonMapper jsonMapper) {
		return new NotificationEmailListener(sender, processedEvents, jsonMapper);
	}

}
