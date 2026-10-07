package com.sealease.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. The docs endpoints are only reachable when {@code springdoc.api-docs.enabled}
 * is true (local profile); see {@link com.sealease.backend.security.SecurityConfig}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	private static final String BEARER = "bearerAuth";

	@Bean
	OpenAPI platformOpenApi(@Value("${app.version:dev}") String version) {
		return new OpenAPI()
			.info(new Info().title("Container Investment Platform API").version(version))
			.components(new Components().addSecuritySchemes(BEARER,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

}
