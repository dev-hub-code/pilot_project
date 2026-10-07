package com.sealease.backend.support;

import com.sealease.backend.common.api.ApiErrorFactory;
import com.sealease.backend.config.ClockConfig;
import com.sealease.backend.security.RestAccessDeniedHandler;
import com.sealease.backend.security.RestAuthenticationEntryPoint;
import com.sealease.backend.security.SecurityConfig;
import com.sealease.backend.security.SecurityWebConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Imports the production security and error-handling beans into {@code @WebMvcTest} slices, so
 * slice tests exercise the real filter chain rather than Spring Boot's test defaults.
 *
 * <p>The slice {@link JwtDecoder} rejects every bearer token; authenticated requests in slice tests
 * use Spring Security's {@code jwt()} request post-processor instead. Real token verification is
 * covered by {@code JwtVerificationTest} and the integration tests.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({ SecurityConfig.class, SecurityWebConfig.class, RestAuthenticationEntryPoint.class,
		RestAccessDeniedHandler.class, ApiErrorFactory.class, ClockConfig.class })
public class WebSliceTestConfig {

	@Bean
	JwtDecoder rejectingJwtDecoder() {
		return token -> {
			throw new BadJwtException("Slice tests do not accept bearer tokens");
		};
	}

}
