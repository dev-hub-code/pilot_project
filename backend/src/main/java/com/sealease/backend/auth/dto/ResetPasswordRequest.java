package com.sealease.backend.auth.dto;

import com.sealease.backend.user.service.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param code the 6-digit code from the password reset email */
public record ResetPasswordRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Pattern(regexp = "\\s*\\d{6}\\s*", message = "Enter the 6-digit code") String code,
		@NotBlank @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.RULES_MESSAGE) String newPassword) {

	@Override
	public String toString() {
		return "ResetPasswordRequest[email=" + email + ", code=***, newPassword=***]";
	}

}
