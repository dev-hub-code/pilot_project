package com.sealease.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * All time-dependent code takes a {@link Clock} so that financial jobs (rental periods, maturity,
 * cart expiry) are testable deterministically.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
