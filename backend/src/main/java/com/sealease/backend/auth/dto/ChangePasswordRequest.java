package com.sealease.backend.auth.dto;

import com.sealease.backend.user.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank @Size(max = 128) String currentPassword,
		@NotBlank @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.RULES_MESSAGE) String newPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[***]";
	}

}
