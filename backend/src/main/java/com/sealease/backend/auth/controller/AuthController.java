package com.sealease.backend.auth.controller;

import com.sealease.backend.auth.dto.AuthTokensResponse;
import com.sealease.backend.auth.dto.ChangePasswordRequest;
import com.sealease.backend.auth.dto.CurrentUserResponse;
import com.sealease.backend.auth.dto.LoginRequest;
import com.sealease.backend.auth.dto.RefreshTokenRequest;
import com.sealease.backend.auth.dto.RegisterRequest;
import com.sealease.backend.auth.service.AuthService;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthTokensResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
		return authService.register(request, ClientInfo.from(http));
	}

	@PostMapping("/login")
	public AuthTokensResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return authService.login(request, ClientInfo.from(http));
	}

	@PostMapping("/refresh")
	public AuthTokensResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
		return authService.refresh(request.refreshToken(), ClientInfo.from(http));
	}

	/** Public so that a client holding only a refresh token (expired access token) can still sign out. */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
	}

	@PostMapping("/logout-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logoutAll(AuthenticatedUser user) {
		authService.logoutAll(user);
	}

	@PostMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(AuthenticatedUser user, @Valid @RequestBody ChangePasswordRequest request) {
		authService.changePassword(user, request);
	}

	@GetMapping("/me")
	public CurrentUserResponse me(AuthenticatedUser user) {
		return authService.currentUser(user);
	}

}
