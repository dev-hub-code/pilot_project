package com.sealease.backend.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * When a fallback SMTP server is set ({@code app.mail.fallback.host}), the app's one {@link JavaMailSender} sends
 * through {@code spring.mail.*} first and the fallback second. Otherwise Spring Boot's own sender is used unchanged.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${app.mail.fallback.host:}' != ''")
@EnableConfigurationProperties(MailProperties.class)
class FailoverMailConfig {

	@Bean
	JavaMailSender mailSender(MailProperties primary, FallbackMailProperties fallback) {
		Properties smtp = new Properties();
		smtp.putAll(primary.getProperties());
		return new FailoverMailSender(
				sender(primary.getHost(), primary.getPort(), primary.getUsername(), primary.getPassword(), smtp),
				sender(fallback.host(), fallback.port(), fallback.username(), fallback.password(), smtp));
	}

	private static JavaMailSenderImpl sender(String host, Integer port, String username, String password,
			Properties smtp) {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(host);
		if (port != null) {
			sender.setPort(port);
		}
		sender.setUsername(username);
		sender.setPassword(password);
		sender.setDefaultEncoding("UTF-8");
		sender.setJavaMailProperties(smtp);
		return sender;
	}

}
