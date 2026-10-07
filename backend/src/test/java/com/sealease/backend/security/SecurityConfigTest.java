package com.sealease.backend.security;

import com.sealease.backend.common.web.CorrelationId;
import com.sealease.backend.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.SecuredController.class,
		properties = "app.security.cors.allowed-origins=https://app.example.com")
@Import({ WebSliceTestConfig.class, SecurityConfigTest.SecuredController.class })
class SecurityConfigTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void anonymousApiRequestGetsJson401WithCorrelationId() throws Exception {
		mvc.perform(get("/api/v1/secured").header(CorrelationId.CORRELATION_HEADER, "trace-abcdef12"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(CorrelationId.CORRELATION_HEADER, "trace-abcdef12"))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.correlationId").value("trace-abcdef12"))
			.andExpect(jsonPath("$.path").value("/api/v1/secured"));
	}

	@Test
	void anonymousRequestToUnknownPathIsStillUnauthorized() throws Exception {
		// Deny-by-default must not reveal which routes exist.
		mvc.perform(get("/api/v1/admin/anything")).andExpect(status().isUnauthorized());
	}

	@Test
	void stateChangingRequestWithoutCsrfTokenIsNotRejectedAsCsrf() throws Exception {
		// Stateless bearer API: an anonymous POST is a 401, never a CSRF 403.
		mvc.perform(post("/api/v1/secured")).andExpect(status().isUnauthorized());
	}

	@Test
	void apiDocsAreNotPublicWhenDisabled() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
	}

	@Test
	void securityHeadersArePresent() throws Exception {
		mvc.perform(get("/api/v1/secured").secure(true))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("Referrer-Policy", "no-referrer"))
			.andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"));
	}

	@Test
	void corsPreflightFromAllowedOriginSucceeds() throws Exception {
		mvc.perform(options("/api/v1/secured")
				.header(HttpHeaders.ORIGIN, "https://app.example.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Idempotency-Key"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.com"));
	}

	@Test
	void corsPreflightFromUnknownOriginIsRejected() throws Exception {
		mvc.perform(options("/api/v1/secured")
				.header(HttpHeaders.ORIGIN, "https://evil.example.net")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@RestController
	static class SecuredController {

		@GetMapping("/api/v1/secured")
		String secured() {
			return "secret";
		}

	}

}
