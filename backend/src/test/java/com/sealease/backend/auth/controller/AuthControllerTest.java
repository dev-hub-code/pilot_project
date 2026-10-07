package com.sealease.backend.auth.controller;

import com.sealease.backend.auth.dto.AuthTokensResponse;
import com.sealease.backend.auth.dto.CurrentUserResponse;
import com.sealease.backend.auth.service.AuthService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.ratelimit.RateLimitExceededException;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.support.TestJwts;
import com.sealease.backend.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(WebSliceTestConfig.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void registrationIsPublicAndReturnsTokens() throws Exception {
		when(authService.register(any(), any())).thenReturn(tokens());

		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"alice@example.com","password":"correct horse battery","firstName":"Alice","lastName":"Lee"}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.accessToken").value("access"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"));
	}

	@Test
	void registrationValidatesBeforeReachingService() throws Exception {
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"not-an-email","password":"short","firstName":"","lastName":"Lee"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.length()").value(3));
		verifyNoInteractions(authService);
	}

	@Test
	void invalidCredentialsMapTo401() throws Exception {
		when(authService.login(any(), any()))
			.thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@example.com\",\"password\":\"wrong-password\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void lockedAccountMapsTo423() throws Exception {
		when(authService.login(any(), any())).thenThrow(new BusinessException(ErrorCode.ACCOUNT_LOCKED, "locked"));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@example.com\",\"password\":\"whatever-password\"}"))
			.andExpect(status().isLocked())
			.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
	}

	@Test
	void rateLimitedLoginReturns429WithRetryAfter() throws Exception {
		when(authService.login(any(), any())).thenThrow(new RateLimitExceededException(Duration.ofSeconds(42)));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@example.com\",\"password\":\"whatever-password\"}"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
			.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
	}

	@Test
	void invalidRefreshTokenMapsTo401() throws Exception {
		when(authService.refresh(any(), any()))
			.thenThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid or expired"));

		mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"abc\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void logoutIsPublicAndIdempotent() throws Exception {
		mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"anything\"}"))
			.andExpect(status().isNoContent());
		verify(authService).logout("anything");
	}

	@Test
	void meRequiresAuthentication() throws Exception {
		mvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void rejectedBearerTokenIsReportedAsInvalidToken() throws Exception {
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void meResolvesCallerFromToken() throws Exception {
		UUID userId = UUID.randomUUID();
		when(authService.currentUser(any())).thenReturn(new CurrentUserResponse(userId, "alice@example.com", "Alice",
				"Lee", "ACTIVE", Set.of("INVESTOR"), Set.of("INVESTOR_PORTAL"), false));

		mvc.perform(get("/api/v1/auth/me").with(TestJwts.userWith(userId, "INVESTOR_PORTAL")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("alice@example.com"));

		ArgumentCaptor<AuthenticatedUser> caller = ArgumentCaptor.forClass(AuthenticatedUser.class);
		verify(authService).currentUser(caller.capture());
		assertThat(caller.getValue().userId()).isEqualTo(userId);
	}

	private static AuthTokensResponse tokens() {
		Instant now = Instant.parse("2026-10-07T10:00:00Z");
		return new AuthTokensResponse("Bearer", "access", now.plusSeconds(900), "refresh", now.plusSeconds(3600));
	}

}
