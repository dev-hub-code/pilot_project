package com.sealease.backend.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Sends all email through SendGrid's Web API when {@code app.mail.sendgrid.api-key} is set. This bean replaces Spring
 * Boot's SMTP sender, so without the key the {@code spring.mail.*} SMTP server is used as before.
 */
@Configuration(proxyBeanMethods = false)
public class SendGridMailConfig {

	@Bean
	@ConditionalOnExpression("!'${app.mail.sendgrid.api-key:}'.isBlank()")
	JavaMailSender sendGridMailSender(SendGridProperties properties) {
		return new SendGridMailSender(properties);
	}

}
