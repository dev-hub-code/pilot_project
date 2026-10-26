package com.sealease.backend.auth.controller;

import com.sealease.backend.auth.dto.AuthTokensResponse;
import com.sealease.backend.auth.dto.ChangePasswordRequest;
import com.sealease.backend.auth.dto.ForgotPasswordRequest;
import com.sealease.backend.auth.dto.CurrentUserResponse;
import com.sealease.backend.auth.dto.LoginRequest;
import com.sealease.backend.auth.dto.RefreshTokenRequest;
import com.sealease.backend.auth.dto.RegisterRequest;
import com.sealease.backend.auth.dto.RegistrationResponse;
import com.sealease.backend.auth.dto.ResendVerificationRequest;
import com.sealease.backend.auth.dto.ResetPasswordRequest;
import com.sealease.backend.auth.dto.VerifyEmailCodeRequest;
import com.sealease.backend.auth.dto.VerifyEmailRequest;
import com.sealease.backend.auth.service.AuthService;
import com.sealease.backend.auth.service.PasswordResetService;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
	private final PasswordResetService passwordReset;

	public AuthController(AuthService authService, PasswordResetService passwordReset) {
		this.authService = authService;
		this.passwordReset = passwordReset;
	}

	/** 201 with tokens, or 202 with {@link RegistrationResponse} when the email address must be confirmed first. */
	@PostMapping("/register")
	public ResponseEntity<Object> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
		Object result = authService.register(request, ClientInfo.from(http));
		return ResponseEntity.status(result instanceof RegistrationResponse ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
			.body(result);
	}

	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		authService.verifyEmail(request.token());
	}

	/** The alternative to the link: the 6-digit code from the same email. */
	@PostMapping("/verify-email/code")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyEmailCode(@Valid @RequestBody VerifyEmailCodeRequest request, HttpServletRequest http) {
		authService.verifyEmailCode(request.email(), request.code(), ClientInfo.from(http));
	}

	/** Always 202, whether or not the address belongs to an unverified account. */
	@PostMapping("/verify-email/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void resendVerificationEmail(@Valid @RequestBody ResendVerificationRequest request, HttpServletRequest http) {
		authService.resendVerificationEmail(request.email(), ClientInfo.from(http));
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
	/** Forgot password: emails a reset code. 404 when no account uses the address, 403 when it is not active. */
	@PostMapping("/password-reset/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void requestPasswordReset(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
		passwordReset.request(request.email(), ClientInfo.from(http));
	}

	/** Sets a new password with the emailed code; every session of the account is signed out. */
	@PostMapping("/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
		passwordReset.reset(request.email(), request.code(), request.newPassword(), ClientInfo.from(http));
	}

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
