package com.sealease.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security baseline for the API.
 *
 * <ul>
 * <li><b>Stateless</b>: no HTTP session; every request carries an RS256 bearer JWT, verified by
 * the {@code JwtDecoder} in {@code auth.jwt.JwtConfig} (signature, issuer, audience, expiry and
 * live session).</li>
 * <li><b>CSRF disabled</b>: the backend never reads cookies for authentication, so it is not
 * CSRF-exposed. Browser cookies live on the Next.js origin, which applies its own CSRF defences
 * (SameSite cookies + Origin checks) before forwarding the bearer token.</li>
 * <li><b>Deny by default</b>: only health probes, the sign-in endpoints, the JWK set, signed
 * payment webhooks and (when enabled) API docs are public.</li>
 * <li><b>Method security</b>: endpoints authorise on fine-grained permissions, e.g.
 * {@code @PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

	private static final String[] PUBLIC_ACTUATOR = { "/actuator/health", "/actuator/health/**", "/actuator/info" };
	private static final String[] PUBLIC_AUTH = { "/api/v1/auth/register", "/api/v1/auth/login",
			"/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/jwks" };
	/** Gateways cannot sign in; webhook deliveries are authenticated by their signature instead. */
	private static final String PAYMENT_WEBHOOKS = "/api/v1/payments/webhooks/*";
	private static final String PUBLIC_LEADS = "/api/v1/public/leads";
	private static final String[] API_DOCS = { "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**" };

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
			RestAuthenticationEntryPoint authenticationEntryPoint,
			RestAccessDeniedHandler accessDeniedHandler,
			@Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.headers(headers -> headers
				.frameOptions(frame -> frame.deny())
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
				.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
			.oauth2ResourceServer(resourceServer -> resourceServer
				.jwt(jwt -> jwt.jwtAuthenticationConverter(new PlatformJwtAuthenticationConverter()))
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.authorizeHttpRequests(auth -> {
				auth.requestMatchers(PUBLIC_ACTUATOR).permitAll();
				auth.requestMatchers(PUBLIC_AUTH).permitAll();
				auth.requestMatchers(HttpMethod.POST, PAYMENT_WEBHOOKS).permitAll();
				auth.requestMatchers(HttpMethod.POST, PUBLIC_LEADS).permitAll();
				if (apiDocsEnabled) {
					auth.requestMatchers(API_DOCS).permitAll();
				}
				auth.anyRequest().authenticated();
			});
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.allowedOrigins());
		config.setAllowedMethods(properties.allowedMethods());
		config.setAllowedHeaders(properties.allowedHeaders());
		config.setExposedHeaders(properties.exposedHeaders());
		config.setAllowCredentials(false);
		config.setMaxAge(properties.maxAge());

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

}
