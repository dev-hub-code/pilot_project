package com.sealease.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

/**
 * JPA auditing ({@code @CreatedDate}/{@code @LastModifiedDate}) driven by the platform {@link Clock}.
 * Kept out of the application class so that web-slice tests do not require JPA.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

	@Bean
	DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(clock.instant());
	}

}
